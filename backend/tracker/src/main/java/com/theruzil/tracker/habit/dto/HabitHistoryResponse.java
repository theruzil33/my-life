package com.theruzil.tracker.habit.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.HabitFrequency;
import com.theruzil.tracker.habit.TimeOfDay;

public record HabitHistoryResponse(Long id, String name, LocalDate startDate, HabitFrequency frequency,
        List<DayOfWeek> weekdays, List<HabitHistoryEntry> days, ActivityType type, List<TimeOfDay> timeOfDay) {
}
