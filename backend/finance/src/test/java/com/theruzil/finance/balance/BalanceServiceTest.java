package com.theruzil.finance.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.finance.balance.dto.BalanceRequest;
import com.theruzil.finance.balance.dto.BalanceResponse;
import com.theruzil.finance.category.CategoryType;
import com.theruzil.finance.operation.OperationRepository;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final Long USER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private OperationRepository operationRepository;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(balanceRepository, operationRepository);
    }

    @Test
    void getUsesZeroStartingBalanceWhenNoneSaved() {
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        whenOperations("50.00", "30.00");

        BalanceResponse response = balanceService.get(USER_ID);

        assertThat(response.startingBalance()).isEqualByComparingTo("0");
        assertThat(response.amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void getComputesBalanceWithStartingBalanceAndOperations() {
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.of(balance("1000.00")));
        whenOperations("500.00", "200.00");

        BalanceResponse response = balanceService.get(USER_ID);

        assertThat(response.startingBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.amount()).isEqualByComparingTo("1300.00");
    }

    @Test
    void getReturnsStartingBalanceWhenNoOperations() {
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.of(balance("250.50")));
        whenOperations("0", "0");

        BalanceResponse response = balanceService.get(USER_ID);

        assertThat(response.amount()).isEqualByComparingTo("250.50");
    }

    @Test
    void getAllowsNegativeBalance() {
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.of(balance("100.00")));
        whenOperations("0", "300.00");

        BalanceResponse response = balanceService.get(USER_ID);

        assertThat(response.amount()).isEqualByComparingTo("-200.00");
    }

    @Test
    void getRecalculatesForEveryCall() {
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.of(balance("100.00")));
        when(operationRepository.sumAmountByUserIdAndCategoryType(USER_ID, CategoryType.INCOME))
                .thenReturn(new BigDecimal("50.00"), new BigDecimal("80.00"));
        when(operationRepository.sumAmountByUserIdAndCategoryType(USER_ID, CategoryType.EXPENSE))
                .thenReturn(BigDecimal.ZERO);

        assertThat(balanceService.get(USER_ID).amount()).isEqualByComparingTo("150.00");
        assertThat(balanceService.get(USER_ID).amount()).isEqualByComparingTo("180.00");
    }

    @Test
    void updateCreatesRowWhenAbsent() {
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        whenOperations("0", "0");

        BalanceResponse response = balanceService.update(USER_ID, new BalanceRequest(new BigDecimal("1000.00")));

        assertThat(response.startingBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.amount()).isEqualByComparingTo("1000.00");

        ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
        verify(balanceRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getStartingBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void updateOverwritesExistingRow() {
        Balance existing = balance("500.00");
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        whenOperations("0", "0");

        BalanceResponse response = balanceService.update(USER_ID, new BalanceRequest(new BigDecimal("1200.00")));

        assertThat(response.startingBalance()).isEqualByComparingTo("1200.00");
        assertThat(existing.getStartingBalance()).isEqualByComparingTo("1200.00");
        verify(balanceRepository).save(existing);
    }

    @Test
    void updateAllowsZeroAndNegative() {
        when(balanceRepository.findById(USER_ID)).thenReturn(Optional.of(balance("500.00")));
        whenOperations("0", "0");

        assertThat(balanceService.update(USER_ID, new BalanceRequest(BigDecimal.ZERO)).startingBalance())
                .isEqualByComparingTo("0");
        assertThat(balanceService.update(USER_ID, new BalanceRequest(new BigDecimal("-250.00"))).startingBalance())
                .isEqualByComparingTo("-250.00");
    }

    @Test
    void updateRejectsMissingRequest() {
        assertThatThrownBy(() -> balanceService.update(USER_ID, null))
                .isInstanceOf(InvalidBalanceDataException.class);
        verifyNoInteractions(balanceRepository, operationRepository);
    }

    @Test
    void updateRejectsMissingValue() {
        assertThatThrownBy(() -> balanceService.update(USER_ID, new BalanceRequest(null)))
                .isInstanceOf(InvalidBalanceDataException.class);
        verifyNoInteractions(balanceRepository, operationRepository);
    }

    @Test
    void updateRejectsAmountWithMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(
                () -> balanceService.update(USER_ID, new BalanceRequest(new BigDecimal("10.123"))))
                .isInstanceOf(InvalidBalanceDataException.class);
        verifyNoInteractions(balanceRepository, operationRepository);
    }

    private void whenOperations(String income, String expense) {
        when(operationRepository.sumAmountByUserIdAndCategoryType(USER_ID, CategoryType.INCOME))
                .thenReturn(new BigDecimal(income));
        when(operationRepository.sumAmountByUserIdAndCategoryType(USER_ID, CategoryType.EXPENSE))
                .thenReturn(new BigDecimal(expense));
    }

    private static Balance balance(String startingBalance) {
        return new Balance(USER_ID, new BigDecimal(startingBalance), NOW);
    }
}
