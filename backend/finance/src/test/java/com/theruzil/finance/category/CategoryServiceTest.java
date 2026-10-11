package com.theruzil.finance.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.finance.category.dto.CategoryOrderRequest;
import com.theruzil.finance.category.dto.CategoryRequest;
import com.theruzil.finance.category.dto.CategoryResponse;
import com.theruzil.finance.operation.OperationRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OperationRepository operationRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, operationRepository);
    }

    @Test
    void createSavesIncomeCategoryForUserAndReturnsResponse() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        CategoryResponse response = categoryService.create(USER_ID,
                new CategoryRequest("INCOME", "Зарплата", null, null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo(CategoryType.INCOME);
        assertThat(response.name()).isEqualTo("Зарплата");

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getType()).isEqualTo(CategoryType.INCOME);
    }

    @Test
    void createSavesExpenseCategory() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 2L));

        CategoryResponse response = categoryService.create(USER_ID,
                new CategoryRequest("EXPENSE", "Продукты", null, null));

        assertThat(response.type()).isEqualTo(CategoryType.EXPENSE);
        assertThat(response.name()).isEqualTo("Продукты");
    }

    @Test
    void createTrimsName() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        CategoryResponse response = categoryService.create(USER_ID,
                new CategoryRequest("INCOME", "  Прочее  ", null, null));

        assertThat(response.name()).isEqualTo("Прочее");
    }

    @Test
    void createSavesAndReturnsGroup() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        CategoryResponse response = categoryService.create(USER_ID,
                new CategoryRequest("INCOME", "Зарплата", "  Работа  ", null));

        assertThat(response.group()).isEqualTo("Работа");
    }

    @Test
    void createWithoutGroupOrWithBlankGroupStoresNoGroup() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 6L));

        CategoryResponse withoutGroup = categoryService.create(USER_ID, new CategoryRequest("INCOME", "A", null, null));
        CategoryResponse blankGroup = categoryService.create(USER_ID, new CategoryRequest("INCOME", "B", "   ", null));

        assertThat(withoutGroup.group()).isNull();
        assertThat(blankGroup.group()).isNull();
    }

    @Test
    void createAllowsDuplicateNames() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 4L));

        categoryService.create(USER_ID, new CategoryRequest("INCOME", "Прочее", "Группа", null));
        categoryService.create(USER_ID, new CategoryRequest("EXPENSE", "Прочее", "Группа", null));

        verify(categoryRepository, org.mockito.Mockito.times(2)).save(any(Category.class));
    }

    @Test
    void createSavesAndReturnsPlannedAmount() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 7L));

        CategoryResponse response = categoryService.create(USER_ID,
                new CategoryRequest("EXPENSE", "Продукты", null, new BigDecimal("15000.00")));

        assertThat(response.plannedAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void createWithoutPlannedAmountStoresNoPlan() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 8L));

        CategoryResponse response = categoryService.create(USER_ID,
                new CategoryRequest("EXPENSE", "Продукты", null, null));

        assertThat(response.plannedAmount()).isNull();
    }

    @Test
    void createAllowsPlannedAmountForBothTypes() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 9L));

        CategoryResponse income = categoryService.create(USER_ID,
                new CategoryRequest("INCOME", "Зарплата", null, new BigDecimal("100.00")));
        CategoryResponse expense = categoryService.create(USER_ID,
                new CategoryRequest("EXPENSE", "Продукты", null, new BigDecimal("200.00")));

        assertThat(income.plannedAmount()).isEqualByComparingTo("100.00");
        assertThat(expense.plannedAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void createRejectsNonPositivePlannedAmount() {
        assertThatThrownBy(() -> categoryService.create(USER_ID,
                new CategoryRequest("EXPENSE", "Продукты", null, BigDecimal.ZERO)))
                .isInstanceOf(InvalidCategoryDataException.class);
        assertThatThrownBy(() -> categoryService.create(USER_ID,
                new CategoryRequest("EXPENSE", "Продукты", null, new BigDecimal("-1.00"))))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsPlannedAmountWithTooManyDecimals() {
        assertThatThrownBy(() -> categoryService.create(USER_ID,
                new CategoryRequest("EXPENSE", "Продукты", null, new BigDecimal("10.555"))))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest("INCOME", "   ", null, null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsMissingName() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest("INCOME", null, null, null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsMissingType() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest(null, "Зарплата", null, null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsUnknownType() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest("SALARY", "Зарплата", null, null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createAssignsSortOrderAfterExistingCategoriesOfType() {
        when(categoryRepository.findTopByUserIdAndTypeOrderBySortOrderDesc(USER_ID, CategoryType.INCOME))
                .thenReturn(Optional.of(categoryWithOrder(1L, USER_ID, CategoryType.INCOME, "Зарплата", null, 4)));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 2L));

        categoryService.create(USER_ID, new CategoryRequest("INCOME", "Премия", null, null));

        assertThat(savedCategory().getSortOrder()).isEqualTo(5);
    }

    @Test
    void createAssignsFirstSortOrderWhenTypeHasNoCategories() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        categoryService.create(USER_ID, new CategoryRequest("EXPENSE", "Продукты", null, null));

        assertThat(savedCategory().getSortOrder()).isEqualTo(1);
    }

    @Test
    void createTakesSortOrderFromItsOwnType() {
        when(categoryRepository.findTopByUserIdAndTypeOrderBySortOrderDesc(USER_ID, CategoryType.EXPENSE))
                .thenReturn(Optional.of(categoryWithOrder(9L, USER_ID, CategoryType.EXPENSE, "Продукты", null, 3)));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        categoryService.create(USER_ID, new CategoryRequest("EXPENSE", "Транспорт", null, null));

        assertThat(savedCategory().getSortOrder()).isEqualTo(4);
        verify(categoryRepository).findTopByUserIdAndTypeOrderBySortOrderDesc(USER_ID, CategoryType.EXPENSE);
    }

    @Test
    void createKeepsAttributesWhenAssigningSortOrder() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        categoryService.create(USER_ID,
                new CategoryRequest("INCOME", "  Зарплата  ", "  Работа  ", new BigDecimal("100.00")));

        Category saved = savedCategory();
        assertThat(saved.getType()).isEqualTo(CategoryType.INCOME);
        assertThat(saved.getName()).isEqualTo("Зарплата");
        assertThat(saved.getGroupName()).isEqualTo("Работа");
        assertThat(saved.getPlannedAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getSortOrder()).isEqualTo(1);
    }

    @Test
    void listOrdersIncomeBeforeExpenseAndKeepsSavedOrderWithinType() {
        when(categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(USER_ID)).thenReturn(List.of(
                categoryWithOrder(1L, USER_ID, CategoryType.EXPENSE, "Продукты", null, 2),
                categoryWithOrder(2L, USER_ID, CategoryType.INCOME, "Зарплата", null, 2),
                categoryWithOrder(3L, USER_ID, CategoryType.EXPENSE, "Транспорт", null, 1),
                categoryWithOrder(4L, USER_ID, CategoryType.INCOME, "Подарок", null, 1)));

        List<CategoryResponse> categories = categoryService.list(USER_ID);

        assertThat(categories).extracting(CategoryResponse::id, CategoryResponse::type)
                .containsExactly(
                        tuple(4L, CategoryType.INCOME),
                        tuple(2L, CategoryType.INCOME),
                        tuple(3L, CategoryType.EXPENSE),
                        tuple(1L, CategoryType.EXPENSE));
    }

    @Test
    void listReturnsPlannedAmountAndAbsence() {
        when(categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(USER_ID)).thenReturn(List.of(
                categoryWithPlan(1L, USER_ID, CategoryType.EXPENSE, "Продукты", null, new BigDecimal("500.00")),
                categoryWithId(2L, USER_ID, CategoryType.INCOME, "Зарплата", null)));

        List<CategoryResponse> categories = categoryService.list(USER_ID);

        assertThat(categories).extracting(CategoryResponse::id, CategoryResponse::plannedAmount)
                .containsExactlyInAnyOrder(
                        tuple(1L, new BigDecimal("500.00")),
                        tuple(2L, null));
    }

    @Test
    void listReturnsEmptyListWhenNoCategories() {
        when(categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(USER_ID)).thenReturn(List.of());

        assertThat(categoryService.list(USER_ID)).isEmpty();
    }

    @Test
    void editChangesNameAndGroupAndKeepsTypeEvenWhenAnotherTypeProvided() {
        Category category = categoryWithId(5L, USER_ID, CategoryType.INCOME, "Зарплата", "Работа");
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.edit(USER_ID, 5L,
                new CategoryRequest("EXPENSE", "Премия", "Бонусы", null));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.type()).isEqualTo(CategoryType.INCOME);
        assertThat(response.name()).isEqualTo("Премия");
        assertThat(response.group()).isEqualTo("Бонусы");
        assertThat(category.getType()).isEqualTo(CategoryType.INCOME);
    }

    @Test
    void editChangesPlannedAmountKeepingType() {
        Category category = categoryWithPlan(5L, USER_ID, CategoryType.INCOME, "Зарплата", "Работа",
                new BigDecimal("100.00"));
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.edit(USER_ID, 5L,
                new CategoryRequest("INCOME", "Зарплата", "Работа", new BigDecimal("250.50")));

        assertThat(response.type()).isEqualTo(CategoryType.INCOME);
        assertThat(response.plannedAmount()).isEqualByComparingTo("250.50");
    }

    @Test
    void editWithNullPlannedAmountClearsPlan() {
        Category category = categoryWithPlan(5L, USER_ID, CategoryType.INCOME, "Зарплата", "Работа",
                new BigDecimal("100.00"));
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.edit(USER_ID, 5L,
                new CategoryRequest("INCOME", "Зарплата", "Работа", null));

        assertThat(response.plannedAmount()).isNull();
        assertThat(category.getPlannedAmount()).isNull();
    }

    @Test
    void editRejectsInvalidPlannedAmount() {
        assertThatThrownBy(() -> categoryService.edit(USER_ID, 5L,
                new CategoryRequest("INCOME", "Зарплата", "Работа", BigDecimal.ZERO)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void editWithBlankGroupClearsGroup() {
        Category category = categoryWithId(5L, USER_ID, CategoryType.INCOME, "Зарплата", "Работа");
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.edit(USER_ID, 5L,
                new CategoryRequest("INCOME", "Зарплата", "   ", null));

        assertThat(response.group()).isNull();
        assertThat(category.getGroupName()).isNull();
    }

    @Test
    void editDoesNotChangeSortOrder() {
        Category category = new Category(USER_ID, CategoryType.INCOME, "Зарплата", "Работа", null, 5, NOW);
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        categoryService.edit(USER_ID, 5L, new CategoryRequest("INCOME", "Премия", "Бонусы", new BigDecimal("250.50")));

        assertThat(category.getSortOrder()).isEqualTo(5);
    }

    @Test
    void editRejectsBlankName() {
        assertThatThrownBy(
                () -> categoryService.edit(USER_ID, 5L, new CategoryRequest("INCOME", "  ", "Группа", null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void editRejectsUnknownOrForeignCategory() {
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> categoryService.edit(USER_ID, 5L, new CategoryRequest("INCOME", "Премия", null, null)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void deleteRemovesOwnCategory() {
        Category category = categoryWithId(5L, USER_ID, CategoryType.INCOME, "Зарплата", null);
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        categoryService.delete(USER_ID, 5L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteRejectsUnknownOrForeignCategory() {
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(USER_ID, 5L))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deleteRejectsCategoryWithOperations() {
        Category category = categoryWithId(5L, USER_ID, CategoryType.INCOME, "Зарплата", null);
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
        when(operationRepository.existsByCategoryId(5L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(USER_ID, 5L))
                .isInstanceOf(CategoryInUseException.class);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void reorderAssignsSortOrderFollowingRequestAndKeepsOtherType() {
        Category first = categoryWithOrder(1L, USER_ID, CategoryType.INCOME, "A", null, 2);
        Category second = categoryWithOrder(2L, USER_ID, CategoryType.INCOME, "B", null, 1);
        Category expense = categoryWithOrder(3L, USER_ID, CategoryType.EXPENSE, "Продукты", null, 4);
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(first));
        when(categoryRepository.findByIdAndUserId(2L, USER_ID)).thenReturn(Optional.of(second));
        when(categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(USER_ID))
                .thenReturn(List.of(first, second, expense));

        categoryService.reorder(USER_ID, new CategoryOrderRequest("INCOME", List.of(2L, 1L)));

        assertThat(second.getSortOrder()).isEqualTo(1);
        assertThat(first.getSortOrder()).isEqualTo(2);
        assertThat(expense.getSortOrder()).isEqualTo(4);
    }

    @Test
    void reorderRejectsForeignOrMissingCategory() {
        when(categoryRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.reorder(USER_ID, new CategoryOrderRequest("INCOME", List.of(99L))))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void reorderRejectsCategoryOfAnotherType() {
        Category expense = categoryWithOrder(1L, USER_ID, CategoryType.EXPENSE, "Продукты", null, 1);
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> categoryService.reorder(USER_ID, new CategoryOrderRequest("INCOME", List.of(1L))))
                .isInstanceOf(InvalidCategoryDataException.class);
    }

    @Test
    void reorderRejectsDuplicateCategoryIds() {
        assertThatThrownBy(() -> categoryService.reorder(USER_ID, new CategoryOrderRequest("INCOME", List.of(1L, 1L))))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void reorderRejectsIncompleteCategorySet() {
        Category first = categoryWithOrder(1L, USER_ID, CategoryType.INCOME, "A", null, 1);
        Category second = categoryWithOrder(2L, USER_ID, CategoryType.INCOME, "B", null, 2);
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(first));
        when(categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(USER_ID)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> categoryService.reorder(USER_ID, new CategoryOrderRequest("INCOME", List.of(1L))))
                .isInstanceOf(InvalidCategoryDataException.class);
    }

    @Test
    void reorderRejectsInvalidTypeOrEmptyIds() {
        assertThatThrownBy(() -> categoryService.reorder(USER_ID, new CategoryOrderRequest("SALARY", List.of(1L))))
                .isInstanceOf(InvalidCategoryDataException.class);
        assertThatThrownBy(() -> categoryService.reorder(USER_ID, new CategoryOrderRequest("INCOME", List.of())))
                .isInstanceOf(InvalidCategoryDataException.class);
        assertThatThrownBy(() -> categoryService.reorder(USER_ID, null))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    private Category savedCategory() {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Category categoryWithId(Long id, Long userId, CategoryType type, String name, String group) {
        Category category = new Category(userId, type, name, group, NOW);
        setId(category, id);
        return category;
    }

    private static Category categoryWithPlan(Long id, Long userId, CategoryType type, String name, String group,
            BigDecimal plannedAmount) {
        Category category = new Category(userId, type, name, group, plannedAmount, NOW);
        setId(category, id);
        return category;
    }

    private static Category categoryWithOrder(Long id, Long userId, CategoryType type, String name, String group,
            int sortOrder) {
        Category category = new Category(userId, type, name, group, null, sortOrder, NOW);
        setId(category, id);
        return category;
    }

    private static Category withId(Category category, Long id) {
        setId(category, id);
        return category;
    }

    private static void setId(Category category, Long id) {
        try {
            Field field = Category.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(category, id);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
