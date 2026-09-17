package com.theruzil.tracker.habit;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "tracker_habits")
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 32)
    private HabitFrequency frequency;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tracker_habit_weekdays", joinColumns = @JoinColumn(name = "habit_id"))
    @Column(name = "day_of_week", nullable = false)
    @Convert(converter = DayOfWeekConverter.class)
    private Set<DayOfWeek> weekdays = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Habit() {
    }

    public Habit(Long userId, String name, LocalDate startDate, HabitFrequency frequency, Set<DayOfWeek> weekdays,
            Instant now) {
        this.userId = userId;
        this.createdAt = now;
        apply(name, startDate, frequency, weekdays, now);
    }

    public void update(String name, LocalDate startDate, HabitFrequency frequency, Set<DayOfWeek> weekdays,
            Instant now) {
        apply(name, startDate, frequency, weekdays, now);
    }

    public boolean isScheduledOn(LocalDate date) {
        if (date.isBefore(startDate)) {
            return false;
        }
        if (frequency == HabitFrequency.DAILY) {
            return true;
        }
        return weekdays.contains(date.getDayOfWeek());
    }

    private void apply(String name, LocalDate startDate, HabitFrequency frequency, Set<DayOfWeek> weekdays,
            Instant now) {
        this.name = name;
        this.startDate = startDate;
        this.frequency = frequency;
        this.weekdays.clear();
        if (frequency == HabitFrequency.SPECIFIC_WEEKDAYS && weekdays != null) {
            this.weekdays.addAll(weekdays);
        }
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public HabitFrequency getFrequency() {
        return frequency;
    }

    public Set<DayOfWeek> getWeekdays() {
        return weekdays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
