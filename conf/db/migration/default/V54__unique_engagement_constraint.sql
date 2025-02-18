-- Removing duplicates before applying constraint
DELETE
FROM report_consumer_review r
    USING (SELECT rr.report_id, MAX(rr.creation_date) AS max_creation
           FROM report_consumer_review rr
           GROUP BY rr.report_id
           HAVING COUNT(1) > 1) agg
WHERE agg.report_id = r.report_id
  AND agg.max_creation <> r.creation_date;

ALTER TABLE report_consumer_review
    ADD CONSTRAINT unique_report_id UNIQUE (report_id);