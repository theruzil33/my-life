package com.theruzil.finance.balance.dto;

import java.math.BigDecimal;

public record BalanceRequest(BigDecimal startingBalance) {
}
