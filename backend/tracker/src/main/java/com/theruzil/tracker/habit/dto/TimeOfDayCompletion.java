package com.theruzil.tracker.habit.dto;

import com.theruzil.tracker.habit.TimeOfDay;

public record TimeOfDayCompletion(TimeOfDay timeOfDay, boolean completed) {
}
