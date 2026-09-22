-- F-03: events (aggregate root) and ticket_tiers (child). A tier row cannot
-- exist without its event: the FK is NOT NULL, and JPA owns the child
-- lifecycle (cascade + orphanRemoval), which is why there is deliberately no
-- `on delete cascade` — a DB cascade would hide whether orphanRemoval works.
create table events
(
    id           uuid primary key,
    -- Placeholder "dev-organizer" until F-07 puts the JWT subject here.
    organizer_id varchar(255)  not null,
    title        varchar(200)  not null,
    description  varchar(2000),
    starts_at    timestamptz   not null,
    -- Same rule as Event's constructor: the DB guards the data no matter who writes it.
    ends_at      timestamptz   not null check (ends_at > starts_at),
    venue_id     uuid          not null references venues (id),
    status       varchar(20)   not null check (status in ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    created_at   timestamptz   not null,
    updated_at   timestamptz   not null
);

-- Backs the F-05 listing ("published events, soonest first"); created now
-- because the spec asks for it with the table, not because F-03 queries it.
create index idx_events_status_starts_at on events (status, starts_at);

create table ticket_tiers
(
    id              uuid primary key,
    event_id        uuid          not null references events (id),
    name            varchar(80)   not null,
    -- Money embeddable: one Java field, two physical columns (amount + ISO-4217 code).
    price_amount    numeric(12, 2) not null check (price_amount >= 0),
    price_currency  char(3)       not null,
    quantity        int           not null check (quantity > 0),
    max_per_booking int           not null check (max_per_booking between 1 and 100)
);

-- Loading an event's tiers is always "where event_id = ?": index the FK.
create index idx_ticket_tiers_event_id on ticket_tiers (event_id);
