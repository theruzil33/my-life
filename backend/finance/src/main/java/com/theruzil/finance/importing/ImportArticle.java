package com.theruzil.finance.importing;

import java.math.BigDecimal;
import java.util.Map;

import com.theruzil.finance.category.CategoryType;

public record ImportArticle(CategoryType type, String name, String group, BigDecimal plannedAmount,
        Map<Integer, BigDecimal> dailyAmounts) {
}
