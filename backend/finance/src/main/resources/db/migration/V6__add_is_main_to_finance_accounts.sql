ALTER TABLE finance_accounts ADD COLUMN is_main BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE finance_accounts a SET is_main = TRUE
WHERE a.id = (SELECT MIN(b.id) FROM finance_accounts b WHERE b.user_id = a.user_id);
