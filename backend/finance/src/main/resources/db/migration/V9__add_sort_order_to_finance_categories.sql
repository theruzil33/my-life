ALTER TABLE finance_categories ADD COLUMN sort_order INTEGER;

UPDATE finance_categories AS c
SET sort_order = (
    SELECT r.rn
    FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, type ORDER BY id) AS rn
        FROM finance_categories
    ) r
    WHERE r.id = c.id
);

ALTER TABLE finance_categories ALTER COLUMN sort_order SET NOT NULL;
