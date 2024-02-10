CREATE TABLE town
(
    id         bigserial PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    name       text                                   NOT NULL
);

CREATE TABLE suburb
(
    id         bigserial PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    town_id    bigint REFERENCES town (id)            NOT NULL,
    name       text                                   NOT NULL
)
