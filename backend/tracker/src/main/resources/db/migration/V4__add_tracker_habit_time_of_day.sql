CREATE TABLE tracker_habit_time_of_day (
    habit_id BIGINT NOT NULL,
    time_of_day VARCHAR(16) NOT NULL,
    CONSTRAINT pk_tracker_habit_time_of_day PRIMARY KEY (habit_id, time_of_day),
    CONSTRAINT ck_tracker_habit_time_of_day CHECK (time_of_day IN ('MORNING', 'DAY', 'EVENING')),
    CONSTRAINT fk_tracker_habit_time_of_day_habit FOREIGN KEY (habit_id) REFERENCES tracker_habits (id) ON DELETE CASCADE
);

ALTER TABLE tracker_habit_completions ADD COLUMN time_of_day VARCHAR(16);

UPDATE tracker_habit_completions SET time_of_day = 'ANY' WHERE time_of_day IS NULL;

ALTER TABLE tracker_habit_completions ALTER COLUMN time_of_day SET DEFAULT 'ANY';
ALTER TABLE tracker_habit_completions ALTER COLUMN time_of_day SET NOT NULL;

ALTER TABLE tracker_habit_completions ADD CONSTRAINT ck_tracker_habit_completions_time_of_day
    CHECK (time_of_day IN ('ANY', 'MORNING', 'DAY', 'EVENING'));

ALTER TABLE tracker_habit_completions DROP CONSTRAINT uq_tracker_habit_completions_habit_date;

ALTER TABLE tracker_habit_completions ADD CONSTRAINT uq_tracker_habit_completions_habit_date_time
    UNIQUE (habit_id, completed_on, time_of_day);
