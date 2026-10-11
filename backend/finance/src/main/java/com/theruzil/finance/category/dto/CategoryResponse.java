package com.theruzil.finance.category.dto;

import com.theruzil.finance.category.CategoryType;

public record CategoryResponse(Long id, CategoryType type, String name, String group) {
}
