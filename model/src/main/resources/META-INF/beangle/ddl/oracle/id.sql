
CREATE SEQUENCE seq_date
    CYCLE
    INCREMENT by   1
    START with 1
    MINVALUE 1
    MAXVALUE 99999999999;
CREATE SEQUENCE SEQ_DATE INCREMENT BY 1 START WITH 1 MAXVALUE 2 MINVALUE 1 CYCLE;

CREATE SEQUENCE seq_datetime
    CYCLE
    INCREMENT by 1
    START with 1
    MINVALUE 1
    MAXVALUE 99999;


CREATE OR REPLACE FUNCTION DATE_ID RETURN NUMBER AS 
  nextid NUMBER;
BEGIN
  select to_number((to_char(sysdate,'yyyyMMdd')||lpad(to_char(seq_date.nextval),11,'0'))) into nextid from dual;
  return nextid;
END DATE_ID;


CREATE OR REPLACE FUNCTION datetime_id RETURN NUMBER AS 
  nextid NUMBER;
BEGIN
  select to_number((to_char(sysdate,'yyyyMMddHH24MISS')||lpad(to_char(seq_datetime.nextval),5,'0'))) into nextid from dual;
  return nextid;
END datetime_id;


 CREATE TABLE  TABLE_SEQUENCES( TABLE_NAME  VARCHAR2(200) NOT NULL,  CURRVAL NUMBER(19,0), 
	 CONSTRAINT TABLE_SEQUENCES_PK PRIMARY KEY (TABLE_NAME));
 
create or replace FUNCTION next_id(tab_name IN VARCHAR2) RETURN NUMBER is
  PRAGMA AUTONOMOUS_TRANSACTION;
  nextid NUMBER(19);
  tablename varchar2(100);
BEGIN
  tablename := lower(tab_name);
  select currval+1 into nextid from table_sequences a where  a.table_name=tablename;
  update table_sequences set currval = currval+1 where table_name = tablename;
  commit;
  RETURN nextid;
  EXCEPTION
  WHEN NO_DATA_FOUND then
   EXECUTE IMMEDIATE 'begin select max(id)+1 into :nextid from '||tablename||'; end;' using out nextid;
    if nextid is null then
      nextid := 1;
    end if;
    insert into table_sequences(table_name,currval) values(tablename,nextid);
    COMMIT;
    RETURN nextid;
  when others then
    raise_application_error(-20011,'Unknown Exception in next_id Function');
END;