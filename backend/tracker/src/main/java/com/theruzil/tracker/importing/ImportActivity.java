package com.theruzil.tracker.importing;

import java.math.BigDecimal;
import java.util.Map;

import com.theruzil.tracker.habit.ActivityType;

public record ImportActivity(ActivityType type, String name, Map<Integer, BigDecimal> dailyValues) {
}
