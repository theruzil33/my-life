package com.theruzil.finance.importing;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.finance.category.CategoryType;
import com.theruzil.finance.category.CategoryService;
import com.theruzil.finance.category.dto.CategoryRequest;
import com.theruzil.finance.category.dto.CategoryResponse;
import com.theruzil.finance.importing.dto.ImportResultResponse;
import com.theruzil.finance.operation.OperationService;
import com.theruzil.finance.operation.dto.OperationRequest;

@Service
public class ImportService {

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 9999;

    private final FinanceCsvParser parser;

    private final CategoryService categoryService;

    private final OperationService operationService;

    public ImportService(FinanceCsvParser parser, CategoryService categoryService, OperationService operationService) {
        this.parser = parser;
        this.categoryService = categoryService;
        this.operationService = operationService;
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

        List<ImportArticle> articles = parser.parse(new InputStreamReader(input, StandardCharsets.UTF_8));
        YearMonth yearMonth = YearMonth.of(year, month);
        int monthLength = yearMonth.lengthOfMonth();

        Map<CategoryKey, Long> existing = new HashMap<>();
        for (CategoryResponse category : categoryService.list(userId)) {
            existing.putIfAbsent(new CategoryKey(category.type(), category.name(), normalize(category.group())),
                    category.id());
        }

        int createdCategories = 0;
        int savedOperations = 0;
        for (ImportArticle article : articles) {
            CategoryKey key = new CategoryKey(article.type(), article.name(), normalize(article.group()));
            Long categoryId = existing.get(key);
            BigDecimal plan = positive(article.plannedAmount());
            if (categoryId == null) {
                CategoryResponse created = categoryService.create(userId,
                        new CategoryRequest(article.type().name(), article.name(), article.group(), plan));
                categoryId = created.id();
                existing.put(key, categoryId);
                createdCategories++;
            }
            else if (plan != null) {
                categoryService.edit(userId, categoryId, new CategoryRequest(null, article.name(), article.group(), plan));
            }

            for (Map.Entry<Integer, BigDecimal> daily : article.dailyAmounts().entrySet()) {
                if (daily.getKey() > monthLength) {
                    continue;
                }
                LocalDate date = yearMonth.atDay(daily.getKey());
                operationService.save(userId, new OperationRequest(categoryId, date, daily.getValue()));
                savedOperations++;
            }
        }
        return new ImportResultResponse(createdCategories, savedOperations);
    }

    private static BigDecimal positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CategoryKey(CategoryType type, String name, String group) {
    }
}
