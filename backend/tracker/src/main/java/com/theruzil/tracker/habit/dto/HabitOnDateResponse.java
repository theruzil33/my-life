package com.theruzil.tracker.habit.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.HabitFrequency;
import com.theruzil.tracker.habit.TimeOfDay;

public record HabitOnDateResponse(Long id, String name, LocalDate startDate, HabitFrequency frequency,
        List<DayOfWeek> weekdays, LocalDate date, boolean completed, ActivityType type, List<TimeOfDay> timeOfDay,
        List<TimeOfDayCompletion> completions) {
}
