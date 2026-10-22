package com.theruzil.tracker.importing;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.theruzil.tracker.habit.ActivityType;

@Component
public class ProductivityCsvParser {

    private static final String HABIT_HEADER = "Привычка";
    private static final String TASK_HEADER = "Задача";
    private static final int NAME_INDEX = 0;
    private static final int VARIANT_INDEX = 1;
    private static final int TYPE_INDEX = 2;
    private static final int DAY_OFFSET = 2;
    private static final int LAST_DAY = 31;

    private final CsvParser csvParser = new CsvParser();

    public List<ImportActivity> parse(Reader reader) {
        List<List<String>> rows;
        try {
            rows = csvParser.parse(reader);
        }
        catch (IOException ex) {
            throw new InvalidImportDataException("Не удалось прочитать файл");
        }

        List<ImportActivity> activities = new ArrayList<>();
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
            activities.add(new ImportActivity(type, displayName(currentName, cell(row, VARIANT_INDEX)),
                    dailyValues(row)));
        }

        if (!foundActivity) {
            throw new InvalidImportDataException("Файл не содержит активностей");
        }
        return activities;
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
}
