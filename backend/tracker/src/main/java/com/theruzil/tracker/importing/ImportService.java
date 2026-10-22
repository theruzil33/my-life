package com.theruzil.tracker.importing;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.tracker.habit.HabitService;
import com.theruzil.tracker.habit.dto.ImportedActivityResult;
import com.theruzil.tracker.importing.dto.ImportResultResponse;

@Service
public class ImportService {

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 9999;

    private final ProductivityCsvParser parser;

    private final HabitService habitService;

    public ImportService(ProductivityCsvParser parser, HabitService habitService) {
        this.parser = parser;
        this.habitService = habitService;
    }

    @Transactional
    public ImportResultResponse importCsv(Long userId, Integer year, Integer month, InputStream input) {
        if (input == null) {
            throw new InvalidImportDataException("Файл не загружен");
        }
        if (year == null || month == null) {
            throw new InvalidImportDataException("Укажите месяц и год");
        }
        if (month < 1 || month > 12) {
            throw new InvalidImportDataException("Месяц должен быть от 1 до 12");
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new InvalidImportDataException("Некорректный год");
        }

        List<ImportActivity> activities = parser.parse(new InputStreamReader(input, StandardCharsets.UTF_8));
        YearMonth yearMonth = YearMonth.of(year, month);
        int monthLength = yearMonth.lengthOfMonth();

        int createdActivities = 0;
        int savedCompletions = 0;
        for (ImportActivity activity : activities) {
            Set<LocalDate> dates = completionDates(activity.dailyValues(), yearMonth, monthLength);
            ImportedActivityResult result = habitService.importActivity(userId, activity.type(), activity.name(),
                    yearMonth.atDay(1), dates);
            if (result.created()) {
                createdActivities++;
            }
            savedCompletions += result.savedCompletions();
        }
        return new ImportResultResponse(createdActivities, savedCompletions);
    }

    private Set<LocalDate> completionDates(Map<Integer, BigDecimal> dailyValues, YearMonth yearMonth, int monthLength) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (Map.Entry<Integer, BigDecimal> daily : dailyValues.entrySet()) {
            Integer day = daily.getKey();
            if (day == null || day < 1 || day > monthLength) {
                continue;
            }
            dates.add(yearMonth.atDay(day));
        }
        return dates;
    }
}
