package com.theruzil.finance.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BalanceRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private BalanceRepository balanceRepository;

    @Test
    void savesAndReadsStartingBalance() {
        Long userId = randomUserId();

        balanceRepository.save(new Balance(userId, new BigDecimal("100.00"), NOW));

        assertThat(balanceRepository.findById(userId))
                .get()
                .extracting(Balance::getStartingBalance)
                .isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void keepsSingleRowPerUserOnUpsert() {
        Long userId = randomUserId();

        balanceRepository.save(new Balance(userId, new BigDecimal("100.00"), NOW));
        balanceRepository.save(new Balance(userId, new BigDecimal("200.00"), NOW));

        assertThat(balanceRepository.findById(userId))
                .get()
                .extracting(Balance::getStartingBalance)
                .isEqualTo(new BigDecimal("200.00"));
        assertThat(balanceRepository.findAll())
                .filteredOn(balance -> balance.getUserId().equals(userId))
                .hasSize(1);
    }

    @Test
    void isolatesBalanceByUser() {
        Long userId = randomUserId();
        Long otherUserId = randomUserId();

        balanceRepository.save(new Balance(userId, new BigDecimal("100.00"), NOW));
        balanceRepository.save(new Balance(otherUserId, new BigDecimal("999.00"), NOW));

        assertThat(balanceRepository.findById(userId)).get()
                .extracting(Balance::getStartingBalance).isEqualTo(new BigDecimal("100.00"));
        assertThat(balanceRepository.findById(otherUserId)).get()
                .extracting(Balance::getStartingBalance).isEqualTo(new BigDecimal("999.00"));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
