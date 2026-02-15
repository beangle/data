CREATE OR REPLACE FUNCTION bit_string( in_num IN NUMBER )
RETURN varchar
IS
  n NUMBER := ABS (in_num);
  ln_max NUMBER := ceil(log(2,n))+1;
  rs VARCHAR2(100) := '';
  p number :=0;
  started number(1) :=0;
BEGIN

FOR i IN REVERSE 0..ln_max LOOP
  p :=  POWER ( 2, i );
  IF n >= p THEN
    rs := rs || '1';
    started := 1;
    n := n - p;
  ELSE
    if started=1 then
      rs := rs || '0';
    end if;
  END IF;
END LOOP;

RETURN  rs;
END bit_string;
/

CREATE OR REPLACE FUNCTION bit_num(bitstr varchar2) RETURN NUMBER IS
tmpVar NUMBER;
BEGIN
   tmpVar := 0;
   select sum(data1) into tmpVar from (select substr(bitstr, rownum, 1) * power(2, length(bitstr) - rownum) data1 from dual
        connect by rownum <= length(bitstr));

   RETURN tmpVar;
   EXCEPTION
     WHEN NO_DATA_FOUND THEN
       NULL;
     WHEN OTHERS THEN
       -- Consider logging the error and then re-raise
       RAISE;
END bit_num;
/

CREATE OR REPLACE FUNCTION str_reverse(s varchar2)
RETURN varchar2
IS
begin
  return utl_raw.cast_to_varchar2(utl_raw.reverse(utl_raw.cast_to_raw(s)));
END str_reverse;
/

CREATE OR REPLACE FUNCTION weekday_of(s date)
RETURN number
IS
begin
  if to_char(s,'D') ='1' then
    return 7;
  else
    return to_char(s,'D')-1 ;
  end if;
END weekday_of;
/

CREATE OR REPLACE FUNCTION weekstate_relative_str(in_num NUMBER, year_start_on date,semester_begin_on date)
RETURN varchar2
IS
  offset NUMBER := to_number(to_char(year_start_on,'D')) - to_number(to_char(semester_begin_on,'D'));
  first_day Date := semester_begin_on;
  start_week number := 0;-- start 1
  weekstate varchar2(30) := '';
  BEGIN
    IF offset < 0 THEN
      offset := offset +7;
    END IF;
    first_day := first_day + offset;
    start_week := (first_day - year_start_on)/7 + 1;

    weekstate := substr(str_reverse(bit_string(in_num)),start_week+1);--oracle substr 1 based ,but weekstate first is 0 weekIdx;
    return weekstate;
END weekstate_relative_str;
/


CREATE OR REPLACE FUNCTION weekstate_start_on(d date) RETURN date AS
   rs date;
   weekspan number :=0;
BEGIN
  weekspan := (trunc(d,'ww') - to_date(to_char(d,'yyyy')||'-01-01','yyyy-MM-dd'))/7;
  rs := d - 7*weekspan;
  RETURN trunc(rs);
END;
/


CREATE OR REPLACE FUNCTION weekstate_build(start_week NUMBER,weeks number,week_cycle number )
RETURN NUMBER
IS
  bin_str VARCHAR2(100) := '';
BEGIN

  bin_str := rpad('0',start_week,'0');

FOR i IN  0..weeks-1 LOOP
  IF week_cycle = 2 and mod((start_week + i),2)=1 THEN
    bin_str := '1'||bin_str;
  elsif week_cycle = 3 and mod((start_week + i),2)=0 THEN
    bin_str := '1'||bin_str;
  elsif week_cycle = 1 or week_cycle=4 then
    bin_str := '1'||bin_str;
  else
    bin_str := '0'||bin_str;
  END IF;
END LOOP;
RETURN  BIT_NUM(bin_str);
END weekstate_build;
/


CREATE OR REPLACE FUNCTION weekstate_of(W IN number) RETURN NUMBER AS
BEGIN
  return power(2,w);
END;
/

CREATE OR REPLACE FUNCTION hourminute_value(hourminutes number)
   RETURN  number
IS
   RESULT         number ;
BEGIN
   result := (hourminutes -mod(hourminutes,100) )*6/10 + mod(hourminutes,100);
   return RESULT;
END;
/

CREATE OR REPLACE FUNCTION hourminute_duration(hm1  number,hm2  number)
   RETURN  number
IS
   RESULT         number ;
BEGIN
   result :=abs(hourminute_value(hm1)-hourminute_value(hm2));
   return RESULT;
END hourminute_duration;
/


CREATE OR REPLACE FUNCTION hourminute_str(hm  number)
   RETURN  varchar2
IS
  str varchar2(20) := lpad(hm,4,'0');
BEGIN
   return substr(str,1,2)||':'||substr(str,3,2);
END;
/

CREATE OR REPLACE FUNCTION weekstate_first(W IN number) RETURN NUMBER AS
BEGIN
  return instr(str_reverse(bit_string(w)),'1')-1; --instr is 1 based
END;
/

CREATE OR REPLACE FUNCTION weekstate_last(W IN number) RETURN NUMBER AS
BEGIN
  return length(bit_string(w))-1;
END;
/
