CREATE OR REPLACE FUNCTION public.my_date_trunc(text, timestamp with time zone)
    RETURNS timestamp with time zone
    LANGUAGE sql
    IMMUTABLE
AS $function$ select date_trunc($1, $2) $function$;