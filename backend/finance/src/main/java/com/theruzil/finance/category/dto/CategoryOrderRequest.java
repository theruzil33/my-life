package com.theruzil.finance.category.dto;

import java.util.List;

public record CategoryOrderRequest(String type, List<Long> categoryIds) {
}
