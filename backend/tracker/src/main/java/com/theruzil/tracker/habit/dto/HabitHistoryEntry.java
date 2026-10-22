package com.theruzil.tracker.habit.dto;

import java.time.LocalDate;

import com.theruzil.tracker.habit.TimeOfDay;

public record HabitHistoryEntry(LocalDate date, boolean completed, TimeOfDay timeOfDay) {

    public HabitHistoryEntry(LocalDate date, boolean completed) {
        this(date, completed, null);
    }
}
