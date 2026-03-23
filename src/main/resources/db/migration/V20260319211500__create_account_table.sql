create table account
(
    id         uuid primary key,
    owner_id   uuid           not null,
    balance    numeric(19, 2) not null default 0.00,
    status     varchar(20)    not null,
    created_at timestamptz    not null,
    updated_at timestamptz    not null
);

create index idx_account_owner_id
    on account (owner_id);