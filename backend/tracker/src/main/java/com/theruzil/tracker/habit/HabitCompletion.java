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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HabitCompletion() {
    }

    public HabitCompletion(Long habitId, LocalDate completedOn, Instant createdAt) {
        this.habitId = habitId;
        this.completedOn = completedOn;
        this.createdAt = createdAt;
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
