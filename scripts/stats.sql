\o ./stats.txt

select current_timestamp "Date de l'extraction";

-- Nb utilisateurs DGCCRF
select count(*) "Nb utilisateurs DGCCRF" from users where role = 'DGCCRF';

-- Nb utilisateurs DGCCRF connectés dans les 60 derniers jours
select count(distinct(u.id)) "Nb utilisateurs DGCCRF connectés dans les 60 derniers jours"
from users u, auth_attempts a
where u.role = 'DGCCRF'
and u.email = a.login
and a.timestamp >= 'now'::timestamp - '60 days'::interval;

-- Nb abonnements DGCCRF
select count(*) "Nb abonnements DGCCRF"
from subscriptions s, users u
where u.role = 'DGCCRF'
and u.id = s.user_id;

-- Nb d'entreprises pour lesquelles un contrôle DGCCRF a été déclaré
select count(distinct company_id) "Nb d''entreprises avec un contrôle DGCCRF déclaré"
from events where action = 'Contrôle effectué';

-- % entreprises signalées avec un compte activé
select ((count(distinct(a.company_id))::numeric / count(distinct(r.company_id))::numeric) * 100)::numeric(5,2) "% entreprises signalées avec un compte activé"
from reports r
left join company_accesses a on r.company_id = a.company_id
where r.company_id is not null;

-- % signalements lus (signalements des 60 derniers jours)
select ((count(*) filter ( where status in ('Signalement transmis', 'Promesse action', 'Signalement infondé', 'Signalement mal attribué', 'Signalement consulté ignoré') ))::numeric /
(count(*) filter ( where status not in ('NA', 'Lanceur d''alerte') ))::numeric * 100)::numeric(5,2) "Signalements des 60 derniers jours : % lus"
from reports
where creation_date >= 'now'::timestamp - '60 days'::interval;

-- % signalements lus avec une réponse (Signalements des 60 derniers jours)
select ((count(*) filter ( where status in ('Promesse action', 'Signalement infondé', 'Signalement mal attribué') ))::numeric /
(count(*) filter ( where status in ('Signalement transmis', 'Promesse action', 'Signalement infondé', 'Signalement mal attribué', 'Signalement consulté ignoré') ))::numeric * 100)::numeric(5,2) "Signalements des 60 derniers jours : % lus avec une réponse"
from reports
where creation_date >= 'now'::timestamp - '60 days'::interval;

-- temps de lecture moyen des signalements (signalements des 60 derniers jours)
select (percentile_cont(0.5) within group ( order by read_delay) / (60 * 24))::numeric(6,2) "Signalements des 60 derniers jours : temps de lecture moyen"
from report_data d, reports r
where r.id = d.report_id
and creation_date >= 'now'::timestamp - '60 days'::interval;

-- répartition des réponses (réponses des 60 derniers jours)
select ((count(1) filter ( where  details->>'responseType' = 'ACCEPTED'))::numeric / count(1) * 100)::numeric(5,2) "Réponses des 60 derniers jours : % promesse d'action ",
((count(1) filter ( where  details->>'responseType' = 'REJECTED'))::numeric / count(1) * 100)::numeric(5,2) "% infondé",
((count(1) filter ( where  details->>'responseType' = 'NOT_CONCERNED'))::numeric / count(1) * 100)::numeric(5,2) "% mal attribué"
from events
where action = 'Réponse du professionnel au signalement'
and creation_date >= 'now'::timestamp - '60 days'::interval;

-- avis sur la réponse du Pro (avis des 60 derniers jours)
select ((count(1) filter ( where  details->>'description' like 'Avis positif%'))::numeric / count(1) * 100)::numeric(5,2) "Avis des 60 derniers jours sur la réponse Pro : % avis positif",
((count(1) filter ( where  details->>'description' like 'Avis négatif%'))::numeric / count(1) * 100)::numeric(5,2) "% avis négatif"
from events
where action = 'Avis du consommateur sur la réponse du professionnel'
and creation_date >= 'now'::timestamp - '60 days'::interval;

-- avis sur les pages d'info
select ((count(1) filter ( where  positive = true))::numeric / count(1) * 100)::numeric(5,2) "Avis des 60 derniers jours sur les pages d'info: % avis positif",
((count(1) filter ( where  positive = false))::numeric / count(1) * 100)::numeric(5,2) "% avis négatif"
from ratings
where creation_date >= 'now'::timestamp - '60 days'::interval;

--  avis sur les pages d'info dispatché par actions (avis des 60 derniers jours)
select category, ((count(1) filter ( where  positive = true))::numeric / count(1) * 100)::numeric(5,2) "Avis des 60 derniers jours sur les pages d'info: % avis positif",
((count(1) filter ( where  positive = false))::numeric / count(1) * 100)::numeric(5,2) "% avis négatif"
from ratings
where creation_date >= 'now'::timestamp - '60 days'::interval
group by category;

\o