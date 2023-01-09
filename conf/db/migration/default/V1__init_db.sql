CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

create table if not exists async_files
(
    id               uuid                     not null
    primary key,
    user_id          uuid                     not null,
    creation_date    timestamp with time zone not null,
    filename         varchar,
    storage_filename varchar,
    kind             varchar
);

create table if not exists auth_attempts
(
    id            uuid                     not null
    primary key,
    login         varchar                  not null,
    timestamp     timestamp with time zone not null,
    is_success    boolean,
    failure_cause varchar
);

create table if not exists auth_tokens
(
    id      uuid                     not null
    primary key,
    user_id uuid                     not null
    constraint user_auth_token
    unique,
    expiry  timestamp with time zone not null
);

create table if not exists companies
(
    id                 uuid                     not null
    primary key,
    siret              varchar                  not null
    unique,
    creation_date      timestamp with time zone not null,
    name               varchar                  not null,
    activity_code      varchar,
    department         varchar,
    street_number      varchar,
    street             varchar,
    address_supplement varchar,
    city               varchar,
    postal_code        varchar,
    is_headoffice      boolean default false    not null,
    is_open            boolean default true     not null,
    is_public          boolean default true     not null
);

create table if not exists access_tokens
(
    id              uuid                     not null
    constraint company_access_tokens_pkey
    primary key,
    company_id      uuid
    constraint "COMPANY_FK"
    references companies
    on update cascade on delete cascade,
    token           varchar                  not null,
    level           varchar,
    valid           boolean                  not null,
    expiration_date timestamp with time zone,
    emailed_to      varchar,
    kind            varchar                  not null,
    creation_date   timestamp with time zone not null
);

create table if not exists company_sync
(
    id           uuid      default uuid_generate_v4()                                 not null
    primary key,
    last_updated timestamp default '1970-01-01 00:00:00'::timestamp without time zone not null
    );

create table if not exists consumer
(
    id            uuid      default uuid_generate_v4() not null
    primary key,
    name          varchar                              not null
    unique,
    creation_date timestamp default now()              not null,
    api_key       varchar                              not null,
    delete_date   timestamp
    );

create table if not exists dgccrf_distribution
(
    code_dept      text,
    nom_dept       text,
    code_region    integer,
    nom_region     text,
    "Code_DGCCRF"  text,
    "Effectif_DD"  integer,
    "Pattern_mail" text
);

create table if not exists emails_validation
(
    id                   uuid                     not null
    primary key,
    creation_date        timestamp with time zone not null,
    confirmation_code    varchar                  not null,
    attempts             integer default 0,
    email                varchar                  not null
    unique,
    last_attempt         timestamp with time zone,
    last_validation_date timestamp with time zone
);

create table if not exists ratings
(
    id            uuid                                   not null
    primary key,
    creation_date timestamp with time zone default now() not null,
    category      varchar                                not null,
    subcategories character varying[],
    positive      boolean                                not null
    );

create table if not exists reports
(
    id                         uuid                                   not null
    constraint signalement_pkey
    primary key,
    type_etablissement         varchar,
    category                   varchar                                not null,
    subcategories              character varying[]                    not null,
    company_name               varchar,
    first_name                 varchar                                not null,
    last_name                  varchar                                not null,
    email                      varchar                                not null,
    creation_date              timestamp with time zone default now() not null,
    contact_agreement          boolean                                not null,
    company_siret              varchar,
    details                    character varying[]                    not null,
    status                     varchar                                not null,
    company_id                 uuid
    constraint signalement_company_id_fkey
    references companies,
    employee_consumer          boolean                                not null,
    website_url                varchar,
    tags                       text[]                   default '{}'::text[],
    vendor                     varchar,
    company_country            varchar,
    phone                      varchar,
    forward_to_reponseconso    boolean                  default false not null,
    company_postal_code        varchar,
    company_street_number      varchar,
    company_street             varchar,
    company_address_supplement varchar,
    company_city               varchar,
    host                       varchar,
    reponseconso_code          text[]                   default '{}'::text[],
    ccrf_code                  text[]                   default '{}'::text[],
    gender                     varchar,
    consumer_reference_number  varchar,
    consumer_phone             varchar,
    company_activity_code      varchar,
    expiration_date            timestamp with time zone               not null
                                             );

create table if not exists report_consumer_review
(
    id            uuid                     not null
    primary key,
    report_id     uuid                     not null
    constraint fk_report_consumer_review
    references reports,
    creation_date timestamp with time zone not null,
    evaluation    varchar                  not null,
    details       varchar
);

create table if not exists report_files
(
    id               uuid                     not null
    constraint piece_jointe_pkey
    primary key,
    report_id        uuid
    constraint fk_report_files
    references reports,
    creation_date    timestamp with time zone not null,
    filename         varchar                  not null,
    origin           varchar                  not null,
    storage_filename varchar                  not null,
    av_output        varchar
);

create index if not exists idx_reportfile_report
    on report_files (report_id);

create index if not exists idx_report_company
    on reports (company_id);

create index if not exists idx_report_host
    on reports (host);

create table if not exists users
(
    id                    uuid                                   not null
    primary key,
    login                 varchar
    constraint login_unique
    unique,
    password              varchar                                not null,
    firstname             varchar                                not null,
    lastname              varchar                                not null,
    role                  varchar                                not null,
    email                 varchar                                not null,
    last_email_validation timestamp with time zone,
    creation_date         timestamp with time zone default now() not null,
    deletion_date         timestamp with time zone
                                        );

create table if not exists company_accesses
(
    company_id    uuid                                   not null
    constraint "COMPANY_FK"
    references companies
    on update cascade on delete cascade,
    user_id       uuid                                   not null
    constraint "USER_FK"
    references users
    on update cascade on delete cascade,
    level         varchar                                not null,
    update_date   timestamp with time zone               not null,
    creation_date timestamp with time zone default now() not null,
    constraint pk_company_user
    primary key (company_id, user_id)
    );

create table if not exists events
(
    id            uuid                                   not null
    primary key,
    report_id     uuid
    constraint fk_events_report
    references reports,
    user_id       uuid
    constraint fk_events_users
    references users,
    creation_date timestamp with time zone default now() not null,
    event_type    varchar                                not null,
    action        varchar                                not null,
    result_action varchar,
    details       jsonb,
    company_id    uuid
    );

create index if not exists idx_event_company
    on events (company_id);

create index if not exists idx_event_report
    on events (report_id);

create table if not exists report_notifications_blocked
(
    user_id       uuid      not null
    constraint fk_report_notifications_blocked_user
    references users,
    company_id    uuid      not null
    constraint fk_report_notifications_blocked_company
    references companies,
    date_creation timestamp not null,
    constraint unique_row
    unique (user_id, company_id)
    );

create table if not exists subscriptions
(
    id            uuid                not null
    primary key,
    user_id       uuid
    constraint fk_subscription_user
    references users,
    departments   character varying[] not null,
    email         varchar,
    categories    character varying[]      default '{}'::character varying[],
    sirets        character varying[]      default '{}'::character varying[],
    frequency     interval            not null,
    creation_date timestamp with time zone default now(),
    countries     character varying[]      default '{}'::character varying[],
    with_tags     character varying[]      default '{}'::character varying[],
    without_tags  character varying[]      default '{}'::character varying[]
    );

create unique index if not exists email_unique_if_not_deleted
    on users (email)
    where (deletion_date IS NULL);

create table if not exists websites
(
    id                    uuid                                                                not null
    primary key,
    creation_date         timestamp with time zone                                            not null,
    company_id            uuid
    references companies,
    kind                  varchar,
    host                  varchar,
    company_country       varchar,
    last_updated          timestamp with time zone default now()                              not null,
    investigation_status  varchar                  default 'NotProcessed'::character varying  not null,
    practice              varchar,
    attribution           varchar,
    identification_status varchar                  default 'NotIdentified'::character varying not null,
    is_marketplace        boolean                  default false,
    constraint company_host
    unique (company_id, host)
    );

create index if not exists idx_website_host
    on websites (host);

