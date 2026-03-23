create table financial_transaction (
                                       id uuid primary key,
                                       account_id uuid not null,
                                       type varchar(20) not null,
                                       amount_value numeric(19,2) not null,
                                       amount_currency varchar(3) not null,
                                       status varchar(20) not null,
                                       idempotency_key varchar(100) not null,
                                       request_hash varchar(128) not null,
                                       created_at timestamptz not null,
                                       constraint fk_financial_transaction_account
                                           foreign key (account_id) references account(id)
);

create index idx_financial_transaction_account_id
    on financial_transaction(account_id);

create index idx_financial_transaction_created_at
    on financial_transaction(created_at);

create unique index uk_financial_transaction_idempotency_key
    on financial_transaction(idempotency_key);