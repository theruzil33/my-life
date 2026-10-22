package com.theruzil.tracker.habit.dto;

import java.time.LocalDate;

import com.theruzil.tracker.habit.TimeOfDay;

public record ImportedCompletion(TimeOfDay timeOfDay, LocalDate date) {
}
