-- Removing duplicates before applying constraint
DELETE
FROM engagement_reviews r
    USING (SELECT rr.report_id, MAX(rr.creation_date) AS max_creation
           FROM engagement_reviews rr
           GROUP BY rr.report_id
           HAVING COUNT(1) > 1) agg
WHERE agg.report_id = r.report_id
  AND agg.max_creation <> r.creation_date;

ALTER TABLE engagement_reviews
    ADD CONSTRAINT unique_engagement_report_id UNIQUE (report_id);