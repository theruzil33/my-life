package com.theruzil.tracker.habit.dto;

import java.time.LocalDate;

public record HabitHistoryEntry(LocalDate date, boolean completed) {
}
