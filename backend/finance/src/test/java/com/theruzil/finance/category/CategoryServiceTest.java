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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        CategoryResponse response = categoryService.create(USER_ID, new CategoryRequest("INCOME", "Зарплата", null));

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

        CategoryResponse response = categoryService.create(USER_ID, new CategoryRequest("EXPENSE", "Продукты", null));

        assertThat(response.type()).isEqualTo(CategoryType.EXPENSE);
        assertThat(response.name()).isEqualTo("Продукты");
    }

    @Test
    void createTrimsName() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        CategoryResponse response = categoryService.create(USER_ID, new CategoryRequest("INCOME", "  Прочее  ", null));

        assertThat(response.name()).isEqualTo("Прочее");
    }

    @Test
    void createSavesAndReturnsGroup() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        CategoryResponse response = categoryService.create(USER_ID, new CategoryRequest("INCOME", "Зарплата", "  Работа  "));

        assertThat(response.group()).isEqualTo("Работа");
    }

    @Test
    void createWithoutGroupOrWithBlankGroupStoresNoGroup() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 6L));

        CategoryResponse withoutGroup = categoryService.create(USER_ID, new CategoryRequest("INCOME", "A", null));
        CategoryResponse blankGroup = categoryService.create(USER_ID, new CategoryRequest("INCOME", "B", "   "));

        assertThat(withoutGroup.group()).isNull();
        assertThat(blankGroup.group()).isNull();
    }

    @Test
    void createAllowsDuplicateNames() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 4L));

        categoryService.create(USER_ID, new CategoryRequest("INCOME", "Прочее", "Группа"));
        categoryService.create(USER_ID, new CategoryRequest("EXPENSE", "Прочее", "Группа"));

        verify(categoryRepository, org.mockito.Mockito.times(2)).save(any(Category.class));
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest("INCOME", "   ", null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsMissingName() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest("INCOME", null, null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsMissingType() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest(null, "Зарплата", null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createRejectsUnknownType() {
        assertThatThrownBy(() -> categoryService.create(USER_ID, new CategoryRequest("SALARY", "Зарплата", null)))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void listOrdersIncomeBeforeExpenseAndKeepsIdOrderWithinType() {
        when(categoryRepository.findByUserIdOrderById(USER_ID)).thenReturn(List.of(
                categoryWithId(1L, USER_ID, CategoryType.EXPENSE, "Продукты", null),
                categoryWithId(2L, USER_ID, CategoryType.INCOME, "Зарплата", null),
                categoryWithId(3L, USER_ID, CategoryType.EXPENSE, "Транспорт", null),
                categoryWithId(4L, USER_ID, CategoryType.INCOME, "Подарок", null)));

        List<CategoryResponse> categories = categoryService.list(USER_ID);

        assertThat(categories).extracting(CategoryResponse::id, CategoryResponse::type)
                .containsExactly(
                        tuple(2L, CategoryType.INCOME),
                        tuple(4L, CategoryType.INCOME),
                        tuple(1L, CategoryType.EXPENSE),
                        tuple(3L, CategoryType.EXPENSE));
    }

    @Test
    void listReturnsEmptyListWhenNoCategories() {
        when(categoryRepository.findByUserIdOrderById(USER_ID)).thenReturn(List.of());

        assertThat(categoryService.list(USER_ID)).isEmpty();
    }

    @Test
    void editChangesNameAndGroupAndKeepsTypeEvenWhenAnotherTypeProvided() {
        Category category = categoryWithId(5L, USER_ID, CategoryType.INCOME, "Зарплата", "Работа");
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.edit(USER_ID, 5L, new CategoryRequest("EXPENSE", "Премия", "Бонусы"));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.type()).isEqualTo(CategoryType.INCOME);
        assertThat(response.name()).isEqualTo("Премия");
        assertThat(response.group()).isEqualTo("Бонусы");
        assertThat(category.getType()).isEqualTo(CategoryType.INCOME);
    }

    @Test
    void editWithBlankGroupClearsGroup() {
        Category category = categoryWithId(5L, USER_ID, CategoryType.INCOME, "Зарплата", "Работа");
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.edit(USER_ID, 5L, new CategoryRequest("INCOME", "Зарплата", "   "));

        assertThat(response.group()).isNull();
        assertThat(category.getGroupName()).isNull();
    }

    @Test
    void editRejectsBlankName() {
        assertThatThrownBy(() -> categoryService.edit(USER_ID, 5L, new CategoryRequest("INCOME", "  ", "Группа")))
                .isInstanceOf(InvalidCategoryDataException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void editRejectsUnknownOrForeignCategory() {
        when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.edit(USER_ID, 5L, new CategoryRequest("INCOME", "Премия", null)))
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

    private static Category categoryWithId(Long id, Long userId, CategoryType type, String name, String group) {
        Category category = new Category(userId, type, name, group, NOW);
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
