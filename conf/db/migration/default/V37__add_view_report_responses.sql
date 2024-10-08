CREATE VIEW report_responses AS
SELECT 
	id as event_id,
	report_id,
	user_id,
	company_id,
	creation_date,
	details->>'responseType' as response_type,
	details->>'responseDetails' as response_details,
	details->>'consumerDetails' as consumer_details,
    details->>'dgccrfDetails' as dgccrf_details
FROM events
WHERE action = 'Réponse du professionnel au signalement';