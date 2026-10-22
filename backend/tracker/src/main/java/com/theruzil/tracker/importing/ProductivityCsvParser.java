package com.theruzil.tracker.importing;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.TimeOfDay;

@Component
public class ProductivityCsvParser {

    private static final String HABIT_HEADER = "Привычка";
    private static final String TASK_HEADER = "Задача";
    private static final int NAME_INDEX = 0;
    private static final int VARIANT_INDEX = 1;
    private static final int TYPE_INDEX = 2;
    private static final int DAY_OFFSET = 2;
    private static final int LAST_DAY = 31;

    private static final Map<String, TimeOfDay> TIME_OF_DAY_VARIANTS = Map.ofEntries(
            Map.entry("УТРОМ", TimeOfDay.MORNING),
            Map.entry("УТРО", TimeOfDay.MORNING),
            Map.entry("MORNING", TimeOfDay.MORNING),
            Map.entry("ДНЁМ", TimeOfDay.DAY),
            Map.entry("ДНЕМ", TimeOfDay.DAY),
            Map.entry("ДЕНЬ", TimeOfDay.DAY),
            Map.entry("DAY", TimeOfDay.DAY),
            Map.entry("ВЕЧЕРОМ", TimeOfDay.EVENING),
            Map.entry("ВЕЧЕР", TimeOfDay.EVENING),
            Map.entry("EVENING", TimeOfDay.EVENING));

    private final CsvParser csvParser = new CsvParser();

    public List<ImportActivity> parse(Reader reader) {
        List<List<String>> rows;
        try {
            rows = csvParser.parse(reader);
        }
        catch (IOException ex) {
            throw new InvalidImportDataException("Не удалось прочитать файл");
        }

        Map<ActivityKey, ActivityBuilder> activities = new LinkedHashMap<>();
        String currentName = null;
        boolean foundActivity = false;
        for (List<String> row : rows) {
            ActivityType type = type(cell(row, TYPE_INDEX));
            if (type == null) {
                continue;
            }
            String nameCell = cell(row, NAME_INDEX);
            if (!nameCell.isEmpty()) {
                currentName = nameCell;
            }
            if (currentName == null || currentName.isEmpty()) {
                continue;
            }
            foundActivity = true;
            String variant = cell(row, VARIANT_INDEX);
            TimeOfDay timeOfDay = timeOfDay(variant);
            String name = timeOfDay == null ? displayName(currentName, variant) : currentName;
            ActivityKey key = new ActivityKey(type, name);
            ActivityBuilder builder = activities.computeIfAbsent(key, ignored -> new ActivityBuilder(type, name));
            if (timeOfDay != null) {
                builder.timeOfDay.add(timeOfDay);
            }
            builder.lines.add(new ImportActivityLine(timeOfDay, dailyValues(row)));
        }

        if (!foundActivity) {
            throw new InvalidImportDataException("Файл не содержит активностей");
        }
        return activities.values().stream().map(ActivityBuilder::build).toList();
    }

    private ActivityType type(String value) {
        if (HABIT_HEADER.equals(value)) {
            return ActivityType.HABIT;
        }
        if (TASK_HEADER.equals(value)) {
            return ActivityType.TASK;
        }
        return null;
    }

    private TimeOfDay timeOfDay(String variant) {
        if (variant.isEmpty()) {
            return null;
        }
        return TIME_OF_DAY_VARIANTS.get(variant.toUpperCase(Locale.ROOT));
    }

    private String displayName(String name, String variant) {
        return variant.isEmpty() ? name : name + " (" + variant + ")";
    }

    private Map<Integer, BigDecimal> dailyValues(List<String> row) {
        Map<Integer, BigDecimal> values = new LinkedHashMap<>();
        for (int day = 1; day <= LAST_DAY; day++) {
            BigDecimal value = parseNumber(cell(row, DAY_OFFSET + day));
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                values.put(day, value);
            }
        }
        return values;
    }

    private BigDecimal parseNumber(String raw) {
        String cleaned = raw.replaceAll("[\\s\\u00A0\\u202F\\u2009]+", "").replace(',', '.');
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        }
        catch (NumberFormatException ex) {
            throw new InvalidImportDataException("Некорректное значение дня: " + raw);
        }
    }

    private String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private record ActivityKey(ActivityType type, String name) {
    }

    private static final class ActivityBuilder {

        private final ActivityType type;

        private final String name;

        private final Set<TimeOfDay> timeOfDay = new LinkedHashSet<>();

        private final List<ImportActivityLine> lines = new ArrayList<>();

        private ActivityBuilder(ActivityType type, String name) {
            this.type = type;
            this.name = name;
        }

        private ImportActivity build() {
            return new ImportActivity(type, name, Set.copyOf(timeOfDay), List.copyOf(lines));
        }
    }
}
