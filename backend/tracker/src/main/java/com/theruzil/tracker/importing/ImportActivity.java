package com.theruzil.tracker.importing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.TimeOfDay;

public record ImportActivity(ActivityType type, String name, Set<TimeOfDay> timeOfDay,
        List<ImportActivityLine> lines) {

    public Map<Integer, BigDecimal> dailyValues() {
        return lines.isEmpty() ? Map.of() : lines.get(0).dailyValues();
    }
}
