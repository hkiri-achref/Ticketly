-- F-04: event lifecycle (publish / cancel) and optimistic locking.
alter table events
    -- @Version column. `default 0` back-fills rows created in F-03: Hibernate
    -- must never read a NULL version, or every UPDATE on those rows would fail.
    add column version             bigint not null default 0,
    add column cancellation_reason varchar(500),
    add column cancelled_at        timestamptz,
    -- The DB agrees with the state machine: cancellation data exists if and
    -- only if the status is CANCELLED, whoever writes the row.
    add constraint chk_events_cancelled
        check ((status = 'CANCELLED') = (cancelled_at is not null and cancellation_reason is not null));
