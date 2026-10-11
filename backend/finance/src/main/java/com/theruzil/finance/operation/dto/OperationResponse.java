package com.theruzil.finance.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OperationResponse(Long id, Long categoryId, LocalDate date, BigDecimal amount) {
}
