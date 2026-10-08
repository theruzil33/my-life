package com.theruzil.tracker.habit.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.HabitFrequency;

public record HabitRequest(String name, LocalDate startDate, HabitFrequency frequency, List<DayOfWeek> weekdays,
        ActivityType type, Boolean markPastCompleted) {

    public HabitRequest(String name, LocalDate startDate, HabitFrequency frequency, List<DayOfWeek> weekdays,
            ActivityType type) {
        this(name, startDate, frequency, weekdays, type, false);
    }
}
