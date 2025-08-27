DROP INDEX company_report_counts_idx;
DROP MATERIALIZED VIEW company_report_counts;

CREATE MATERIALIZED VIEW company_report_counts AS
select c."id"                     as company_id,
       count(r."id")              as total_reports,
       count((case
                  when (r."id" is not null) then (case
                                                      when (r."status" in ('PromesseAction', 'Infonde', 'MalAttribue'))
                                                          then r."id" end)
                  else null end)) as total_processed_reports,
       count((case
                  when (r."id" is not null) then (case
                                                      when (r."status" not in ('NA', 'InformateurInterne', 'SuppressionRGPD'))
                                                          then r."id" end)
                  else null end)) as total_transmitted_reports

from "companies" c
         left outer join "reports" r on c."id" = r."company_id"
group by c."id";

-- Needed to use REFRESH MATERIALIZED VIEW CONCURRENTLY to be able to access old data while refreshing
CREATE UNIQUE INDEX company_report_counts_idx ON company_report_counts (company_id);