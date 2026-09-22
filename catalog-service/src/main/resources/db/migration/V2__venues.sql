-- F-02: venues table. The Address embeddable record contributes the three
-- street/city/country columns — one Java field, three physical columns.
create table venues
(
    id         uuid primary key,
    name       varchar(120) not null,
    street     varchar(255) not null,
    city       varchar(120) not null,
    country    varchar(120) not null,
    -- The check mirrors bean validation on purpose: validation guards the API
    -- edge, the constraint guards the data no matter who writes it.
    capacity   int          not null check (capacity between 1 and 200000),
    created_at timestamptz  not null
);

-- Backs GET /api/v1/venues?city=… . Spring Data's IgnoreCase derived query
-- compares with upper(), so the functional index must use upper() too.
create index idx_venues_city_upper on venues (upper(city));
