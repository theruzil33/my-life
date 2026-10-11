package com.theruzil.finance.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CategoryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private CategoryRepository categoryRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = randomUserId();
    }

    @Test
    void findByUserIdOrderBySortOrderAscIdAscReturnsOnlyCategoriesOfUser() {
        Category first = save(userId, CategoryType.INCOME, "Зарплата");
        Category second = save(userId, CategoryType.EXPENSE, "Продукты");
        save(randomUserId(), CategoryType.EXPENSE, "Чужая");

        List<Category> categories = categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(userId);

        assertThat(categories).extracting(Category::getId).containsExactly(first.getId(), second.getId());
        assertThat(categories).extracting(Category::getName).containsExactly("Зарплата", "Продукты");
    }

    @Test
    void ordersBySortOrderThenId() {
        Category third = categoryRepository
                .save(new Category(userId, CategoryType.INCOME, "Третья", null, null, 3, NOW));
        Category first = categoryRepository
                .save(new Category(userId, CategoryType.INCOME, "Первая", null, null, 1, NOW));
        Category second = categoryRepository
                .save(new Category(userId, CategoryType.INCOME, "Вторая", null, null, 1, NOW));

        List<Category> categories = categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(userId);

        assertThat(categories).extracting(Category::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void findsMaxSortOrderForUserAndType() {
        categoryRepository.save(new Category(userId, CategoryType.INCOME, "Зарплата", null, null, 2, NOW));
        Category highest = categoryRepository
                .save(new Category(userId, CategoryType.INCOME, "Премия", null, null, 5, NOW));
        categoryRepository.save(new Category(userId, CategoryType.EXPENSE, "Продукты", null, null, 9, NOW));

        assertThat(categoryRepository.findTopByUserIdAndTypeOrderBySortOrderDesc(userId, CategoryType.INCOME))
                .contains(highest);
        assertThat(categoryRepository.findTopByUserIdAndTypeOrderBySortOrderDesc(randomUserId(), CategoryType.INCOME))
                .isEmpty();
    }

    @Test
    void findByIdAndUserIdDoesNotReturnForeignCategory() {
        Category category = save(userId, CategoryType.INCOME, "Зарплата");

        assertThat(categoryRepository.findByIdAndUserId(category.getId(), userId)).contains(category);
        assertThat(categoryRepository.findByIdAndUserId(category.getId(), randomUserId())).isEmpty();
    }

    @Test
    void persistsGroupNameIncludingAbsence() {
        Category grouped = categoryRepository.save(new Category(userId, CategoryType.INCOME, "Зарплата", "Работа", NOW));
        Category ungrouped = categoryRepository.save(new Category(userId, CategoryType.EXPENSE, "Продукты", null, NOW));

        assertThat(categoryRepository.findByIdAndUserId(grouped.getId(), userId)).get()
                .extracting(Category::getGroupName).isEqualTo("Работа");
        assertThat(categoryRepository.findByIdAndUserId(ungrouped.getId(), userId)).get()
                .extracting(Category::getGroupName).isNull();
    }

    @Test
    void persistsPlannedAmountIncludingAbsence() {
        Category planned = categoryRepository.save(
                new Category(userId, CategoryType.EXPENSE, "Продукты", null, new java.math.BigDecimal("15000.00"), NOW));
        Category withoutPlan = categoryRepository.save(
                new Category(userId, CategoryType.INCOME, "Зарплата", null, null, NOW));

        assertThat(categoryRepository.findByIdAndUserId(planned.getId(), userId)).get()
                .extracting(Category::getPlannedAmount).isEqualTo(new java.math.BigDecimal("15000.00"));
        assertThat(categoryRepository.findByIdAndUserId(withoutPlan.getId(), userId)).get()
                .extracting(Category::getPlannedAmount).isNull();
    }

    private Category save(Long ownerId, CategoryType type, String name) {
        return categoryRepository.save(new Category(ownerId, type, name, null, NOW));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
