package com.theruzil.finance.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.finance.category.Category;
import com.theruzil.finance.category.CategoryRepository;
import com.theruzil.finance.category.CategoryType;

@SpringBootTest
@Transactional
class OperationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long userId;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        userId = randomUserId();
        categoryId = categoryRepository
                .save(new Category(userId, CategoryType.EXPENSE, "Продукты", null, NOW)).getId();
    }

    @Test
    void existsByCategoryIdReflectsOperations() {
        assertThat(operationRepository.existsByCategoryId(categoryId)).isFalse();

        operationRepository.save(new Operation(userId, categoryId, LocalDate.parse("2026-05-13"),
                new BigDecimal("100.00"), NOW));

        assertThat(operationRepository.existsByCategoryId(categoryId)).isTrue();
        assertThat(operationRepository.existsByCategoryId(randomUserId())).isFalse();
    }

    @Test
    void findByUserIdAndOperationDateOrderByIdReturnsOnlyRequestedDateAndUser() {
        Operation first = save(userId, categoryId, "2026-05-13", "10.00");
        Operation other = save(userId, categoryId, "2026-05-14", "20.00");
        Long otherCategory = categoryRepository
                .save(new Category(randomUserId(), CategoryType.INCOME, "Чужая", null, NOW)).getId();
        save(randomUserId(), otherCategory, "2026-05-13", "30.00");

        List<Operation> operations = operationRepository.findByUserIdAndOperationDateOrderById(userId,
                LocalDate.parse("2026-05-13"));

        assertThat(operations).extracting(Operation::getId).containsExactly(first.getId());
        assertThat(operations).extracting(Operation::getId).doesNotContain(other.getId());
    }

    @Test
    void findByUserIdAndOperationDateBetweenOrderByIdReturnsRangeInclusive() {
        Operation inRange = save(userId, categoryId, "2026-05-15", "10.00");
        Long secondCategory = categoryRepository
                .save(new Category(userId, CategoryType.INCOME, "Зарплата", null, NOW)).getId();
        Operation outOfRange = save(userId, secondCategory, "2026-06-01", "20.00");

        List<Operation> operations = operationRepository.findByUserIdAndOperationDateBetweenOrderById(userId,
                LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"));

        assertThat(operations).extracting(Operation::getId).containsExactly(inRange.getId());
        assertThat(operations).extracting(Operation::getId).doesNotContain(outOfRange.getId());
    }

    @Test
    void sumAmountByUserIdAndCategoryTypeSumsOnlyUserAndType() {
        Long incomeCategory = categoryRepository
                .save(new Category(userId, CategoryType.INCOME, "Зарплата", null, NOW)).getId();
        save(userId, categoryId, "2026-05-13", "40.00");
        save(userId, categoryId, "2026-05-14", "5.00");
        save(userId, incomeCategory, "2026-05-13", "100.00");

        Long foreignCategory = categoryRepository
                .save(new Category(randomUserId(), CategoryType.EXPENSE, "Чужая", null, NOW)).getId();
        save(randomUserId(), foreignCategory, "2026-05-13", "999.00");

        assertThat(operationRepository.sumAmountByUserIdAndCategoryType(userId, CategoryType.EXPENSE))
                .isEqualByComparingTo("45.00");
        assertThat(operationRepository.sumAmountByUserIdAndCategoryType(userId, CategoryType.INCOME))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void sumAmountByUserIdAndCategoryTypeIsZeroWhenNoOperations() {
        assertThat(operationRepository.sumAmountByUserIdAndCategoryType(userId, CategoryType.INCOME))
                .isEqualByComparingTo("0");
    }

    @Test
    void uniqueCategoryAndDateConstraintRejectsDuplicate() {
        save(userId, categoryId, "2026-05-13", "10.00");

        assertThatThrownBy(() -> operationRepository.saveAndFlush(
                new Operation(userId, categoryId, LocalDate.parse("2026-05-13"), new BigDecimal("20.00"), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Operation save(Long ownerId, Long catId, String date, String amount) {
        return operationRepository.saveAndFlush(
                new Operation(ownerId, catId, LocalDate.parse(date), new BigDecimal(amount), NOW));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
