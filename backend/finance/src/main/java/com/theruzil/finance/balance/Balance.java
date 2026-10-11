package com.theruzil.finance.balance;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finance_balance")
public class Balance {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "starting_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal startingBalance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Balance() {
    }

    public Balance(Long userId, BigDecimal startingBalance, Instant now) {
        this.userId = userId;
        this.startingBalance = startingBalance;
        this.updatedAt = now;
    }

    public void updateStartingBalance(BigDecimal startingBalance, Instant now) {
        this.startingBalance = startingBalance;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getStartingBalance() {
        return startingBalance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
