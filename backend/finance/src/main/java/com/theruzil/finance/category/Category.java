package com.theruzil.finance.category;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finance_categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CategoryType type;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "group_name", length = 200)
    private String groupName;

    @Column(name = "planned_amount")
    private BigDecimal plannedAmount;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public Category(Long userId, CategoryType type, String name, String groupName, Instant now) {
        this(userId, type, name, groupName, null, now);
    }

    public Category(Long userId, CategoryType type, String name, String groupName, BigDecimal plannedAmount,
            Instant now) {
        this(userId, type, name, groupName, plannedAmount, 0, now);
    }

    public Category(Long userId, CategoryType type, String name, String groupName, BigDecimal plannedAmount,
            Integer sortOrder, Instant now) {
        this.userId = userId;
        this.type = type;
        this.name = name;
        this.groupName = groupName;
        this.plannedAmount = plannedAmount;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(String name, String groupName, BigDecimal plannedAmount, Instant now) {
        this.name = name;
        this.groupName = groupName;
        this.plannedAmount = plannedAmount;
        this.updatedAt = now;
    }

    public void assignSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public CategoryType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getGroupName() {
        return groupName;
    }

    public BigDecimal getPlannedAmount() {
        return plannedAmount;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
