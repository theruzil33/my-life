package com.theruzil.tracker.habit.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.theruzil.tracker.habit.HabitFrequency;

public record HabitResponse(Long id, String name, LocalDate startDate, HabitFrequency frequency,
        List<DayOfWeek> weekdays) {
}
