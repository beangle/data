CREATE OR REPLACE FUNCTION public.add_seconds(
  timestamp without time zone,
  integer)
RETURNS timestamp without time zone
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE
AS $BODY$
begin
  return $1 +  $2 * (interval '1' second);
end;
$BODY$;
