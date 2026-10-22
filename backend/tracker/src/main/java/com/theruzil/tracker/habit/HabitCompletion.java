package com.theruzil.tracker.habit;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tracker_habit_completions")
public class HabitCompletion {

    public static final String ANY_TIME_OF_DAY = "ANY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "time_of_day", nullable = false, length = 16)
    private String timeOfDayValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HabitCompletion() {
    }

    public HabitCompletion(Long habitId, LocalDate completedOn, TimeOfDay timeOfDay, Instant createdAt) {
        this.habitId = habitId;
        this.completedOn = completedOn;
        this.timeOfDayValue = storageValue(timeOfDay);
        this.createdAt = createdAt;
    }

    public HabitCompletion(Long habitId, LocalDate completedOn, Instant createdAt) {
        this(habitId, completedOn, null, createdAt);
    }

    public TimeOfDay getTimeOfDay() {
        return ANY_TIME_OF_DAY.equals(timeOfDayValue) ? null : TimeOfDay.valueOf(timeOfDayValue);
    }

    public String getTimeOfDayValue() {
        return timeOfDayValue;
    }

    public static String storageValue(TimeOfDay timeOfDay) {
        return timeOfDay == null ? ANY_TIME_OF_DAY : timeOfDay.name();
    }

    public Long getId() {
        return id;
    }

    public Long getHabitId() {
        return habitId;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
