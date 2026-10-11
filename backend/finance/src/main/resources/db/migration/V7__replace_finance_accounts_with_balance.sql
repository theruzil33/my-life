DROP TABLE finance_accounts;

CREATE TABLE finance_balance (
    user_id BIGINT PRIMARY KEY,
    starting_balance NUMERIC(19, 2) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
