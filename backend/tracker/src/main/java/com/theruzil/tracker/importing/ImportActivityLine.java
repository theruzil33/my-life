package com.theruzil.tracker.importing;

import java.math.BigDecimal;
import java.util.Map;

import com.theruzil.tracker.habit.TimeOfDay;

public record ImportActivityLine(TimeOfDay timeOfDay, Map<Integer, BigDecimal> dailyValues) {
}
