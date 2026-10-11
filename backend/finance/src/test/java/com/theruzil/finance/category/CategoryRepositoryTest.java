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
    void findByUserIdOrderByIdReturnsOnlyCategoriesOfUser() {
        Category first = save(userId, CategoryType.INCOME, "Зарплата");
        Category second = save(userId, CategoryType.EXPENSE, "Продукты");
        save(randomUserId(), CategoryType.EXPENSE, "Чужая");

        List<Category> categories = categoryRepository.findByUserIdOrderById(userId);

        assertThat(categories).extracting(Category::getId).containsExactly(first.getId(), second.getId());
        assertThat(categories).extracting(Category::getName).containsExactly("Зарплата", "Продукты");
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

    private Category save(Long ownerId, CategoryType type, String name) {
        return categoryRepository.save(new Category(ownerId, type, name, null, NOW));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
