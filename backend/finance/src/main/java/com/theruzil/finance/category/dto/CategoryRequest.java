package com.theruzil.finance.category.dto;

import java.math.BigDecimal;

public record CategoryRequest(String type, String name, String group, BigDecimal plannedAmount) {
}
