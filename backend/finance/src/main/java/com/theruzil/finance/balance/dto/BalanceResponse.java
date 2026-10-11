package com.theruzil.finance.balance.dto;

import java.math.BigDecimal;

public record BalanceResponse(BigDecimal startingBalance, BigDecimal amount) {
}
