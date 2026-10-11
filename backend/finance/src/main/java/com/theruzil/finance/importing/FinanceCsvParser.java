package com.theruzil.finance.importing;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.theruzil.finance.category.CategoryType;

@Component
public class FinanceCsvParser {

    private static final String INCOME_HEADER = "Статьи доходов";
    private static final String EXPENSE_HEADER = "Статьи расходов";
    private static final int GROUP_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int PLAN_INDEX = 2;
    private static final int FIRST_DAY_INDEX = 5;
    private static final int LAST_DAY = 31;

    private final CsvParser csvParser = new CsvParser();

    public List<ImportArticle> parse(Reader reader) {
        List<List<String>> rows;
        try {
            rows = csvParser.parse(reader);
        }
        catch (IOException ex) {
            throw new InvalidImportDataException("Не удалось прочитать файл");
        }

        List<ImportArticle> articles = new ArrayList<>();
        CategoryType type = null;
        String group = null;
        boolean foundSection = false;
        for (List<String> row : rows) {
            CategoryType section = section(cell(row, GROUP_INDEX));
            if (section != null) {
                type = section;
                group = null;
                foundSection = true;
                continue;
            }
            if (type == null) {
                continue;
            }
            String groupCell = cell(row, GROUP_INDEX);
            String nameCell = cell(row, NAME_INDEX);
            if (groupCell.isEmpty() && nameCell.isEmpty()) {
                continue;
            }
            String name;
            String articleGroup;
            if (!nameCell.isEmpty()) {
                if (!groupCell.isEmpty()) {
                    group = groupCell;
                }
                name = nameCell;
                articleGroup = group;
            }
            else {
                group = groupCell;
                name = groupCell;
                articleGroup = null;
            }
            articles.add(new ImportArticle(type, name, articleGroup, parseNumber(cell(row, PLAN_INDEX)),
                    dailyAmounts(row)));
        }

        if (!foundSection || articles.isEmpty()) {
            throw new InvalidImportDataException("Файл не содержит статей доходов и расходов");
        }
        return articles;
    }

    private CategoryType section(String value) {
        if (INCOME_HEADER.equals(value)) {
            return CategoryType.INCOME;
        }
        if (EXPENSE_HEADER.equals(value)) {
            return CategoryType.EXPENSE;
        }
        return null;
    }

    private Map<Integer, BigDecimal> dailyAmounts(List<String> row) {
        Map<Integer, BigDecimal> amounts = new LinkedHashMap<>();
        for (int day = 1; day <= LAST_DAY; day++) {
            BigDecimal amount = parseNumber(cell(row, 4 + day));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                amounts.put(day, amount);
            }
        }
        return amounts;
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
            throw new InvalidImportDataException("Некорректное числовое значение: " + raw);
        }
    }

    private String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }
}
