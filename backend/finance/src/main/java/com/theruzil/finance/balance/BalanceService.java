package com.theruzil.finance.balance;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.finance.balance.dto.BalanceRequest;
import com.theruzil.finance.balance.dto.BalanceResponse;
import com.theruzil.finance.category.CategoryType;
import com.theruzil.finance.operation.OperationRepository;

@Service
public class BalanceService {

    private static final int MAX_SCALE = 2;

    private final BalanceRepository balanceRepository;

    private final OperationRepository operationRepository;

    public BalanceService(BalanceRepository balanceRepository, OperationRepository operationRepository) {
        this.balanceRepository = balanceRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional(readOnly = true)
    public BalanceResponse get(Long userId) {
        BigDecimal startingBalance = startingBalance(userId);
        return toResponse(userId, startingBalance);
    }

    @Transactional
    public BalanceResponse update(Long userId, BalanceRequest request) {
        BigDecimal startingBalance = validate(request);
        Instant now = Instant.now();
        Balance balance = balanceRepository.findById(userId).orElse(null);
        if (balance == null) {
            balanceRepository.save(new Balance(userId, startingBalance, now));
        }
        else {
            balance.updateStartingBalance(startingBalance, now);
            balanceRepository.save(balance);
        }
        return toResponse(userId, startingBalance);
    }

    private BigDecimal startingBalance(Long userId) {
        return balanceRepository.findById(userId)
                .map(Balance::getStartingBalance)
                .orElse(BigDecimal.ZERO);
    }

    private BalanceResponse toResponse(Long userId, BigDecimal startingBalance) {
        BigDecimal income = operationRepository.sumAmountByUserIdAndCategoryType(userId, CategoryType.INCOME);
        BigDecimal expense = operationRepository.sumAmountByUserIdAndCategoryType(userId, CategoryType.EXPENSE);
        return new BalanceResponse(startingBalance, startingBalance.add(income).subtract(expense));
    }

    private BigDecimal validate(BalanceRequest request) {
        if (request == null || request.startingBalance() == null) {
            throw new InvalidBalanceDataException("Starting balance is required");
        }
        if (request.startingBalance().scale() > MAX_SCALE) {
            throw new InvalidBalanceDataException("Starting balance must have at most two decimal places");
        }
        return request.startingBalance();
    }
}
