package com.theruzil.tracker.habit.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.HabitFrequency;
import com.theruzil.tracker.habit.TimeOfDay;

public record HabitRequest(String name, LocalDate startDate, HabitFrequency frequency, List<DayOfWeek> weekdays,
        ActivityType type, List<TimeOfDay> timeOfDay, Boolean markPastCompleted) {

    public HabitRequest(String name, LocalDate startDate, HabitFrequency frequency, List<DayOfWeek> weekdays,
            ActivityType type) {
        this(name, startDate, frequency, weekdays, type, null, false);
    }

    public HabitRequest(String name, LocalDate startDate, HabitFrequency frequency, List<DayOfWeek> weekdays,
            ActivityType type, Boolean markPastCompleted) {
        this(name, startDate, frequency, weekdays, type, null, markPastCompleted);
    }

    public HabitRequest(String name, LocalDate startDate, HabitFrequency frequency, List<DayOfWeek> weekdays,
            ActivityType type, List<TimeOfDay> timeOfDay) {
        this(name, startDate, frequency, weekdays, type, timeOfDay, false);
    }
}
