package com.theruzil.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.finance.category.CategoryService;
import com.theruzil.finance.category.CategoryType;
import com.theruzil.finance.category.dto.CategoryRequest;
import com.theruzil.finance.category.dto.CategoryResponse;
import com.theruzil.finance.importing.dto.ImportResultResponse;
import com.theruzil.finance.operation.OperationService;
import com.theruzil.finance.operation.dto.OperationRequest;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    private static final Long USER_ID = 10L;
    private static final int YEAR = 2026;
    private static final int MONTH = 5;

    @Mock
    private CategoryService categoryService;

    @Mock
    private OperationService operationService;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ImportService(new FinanceCsvParser(), categoryService, operationService);
    }

    @Test
    void createsMissingCategoryAndOperations() {
        when(categoryService.list(USER_ID)).thenReturn(List.of());
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(1L, CategoryType.EXPENSE, "Продукты", "Еда", new BigDecimal("100.00")));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv("Статьи расходов", article("Еда", "Продукты", "100", "10", "", "20")));

        assertThat(result.createdCategories()).isEqualTo(1);
        assertThat(result.savedOperations()).isEqualTo(2);

        ArgumentCaptor<CategoryRequest> request = ArgumentCaptor.forClass(CategoryRequest.class);
        verify(categoryService).create(eq(USER_ID), request.capture());
        assertThat(request.getValue().type()).isEqualTo("EXPENSE");
        assertThat(request.getValue().name()).isEqualTo("Продукты");
        assertThat(request.getValue().group()).isEqualTo("Еда");
        assertThat(request.getValue().plannedAmount()).isEqualByComparingTo("100.00");

        ArgumentCaptor<OperationRequest> operations = ArgumentCaptor.forClass(OperationRequest.class);
        verify(operationService, times(2)).save(eq(USER_ID), operations.capture());
        assertThat(operations.getAllValues()).extracting(OperationRequest::categoryId).containsOnly(1L);
        Map<LocalDate, BigDecimal> amounts = operations.getAllValues().stream()
                .collect(Collectors.toMap(OperationRequest::date, OperationRequest::amount));
        assertThat(amounts).containsOnlyKeys(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));
        assertThat(amounts.get(LocalDate.of(2026, 5, 1))).isEqualByComparingTo("10");
        assertThat(amounts.get(LocalDate.of(2026, 5, 3))).isEqualByComparingTo("20");
    }

    @Test
    void reusesExistingCategoryByTypeNameAndGroup() {
        when(categoryService.list(USER_ID))
                .thenReturn(List.of(new CategoryResponse(7L, CategoryType.EXPENSE, "Продукты", "Еда", null)));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv("Статьи расходов", article("Еда", "Продукты", "0", "5")));

        assertThat(result.createdCategories()).isZero();
        assertThat(result.savedOperations()).isEqualTo(1);
        verify(categoryService, never()).create(any(), any());
        verify(operationService).save(eq(USER_ID), any(OperationRequest.class));
    }

    @Test
    void createsNewCategoryWhenTypeOrGroupDiffers() {
        when(categoryService.list(USER_ID))
                .thenReturn(List.of(new CategoryResponse(7L, CategoryType.INCOME, "Продукты", "Еда", null)));
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(8L, CategoryType.EXPENSE, "Продукты", "Еда", null));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv("Статьи расходов", article("Еда", "Продукты", "0", "5")));

        assertThat(result.createdCategories()).isEqualTo(1);
        verify(categoryService).create(eq(USER_ID), any(CategoryRequest.class));
    }

    @Test
    void updatesPlanOfExistingCategoryWhenPositive() {
        when(categoryService.list(USER_ID)).thenReturn(
                List.of(new CategoryResponse(7L, CategoryType.EXPENSE, "Продукты", null, new BigDecimal("50.00"))));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv("Статьи расходов", article("Продукты", "", "100", "5")));

        assertThat(result.createdCategories()).isZero();
        ArgumentCaptor<CategoryRequest> request = ArgumentCaptor.forClass(CategoryRequest.class);
        verify(categoryService).edit(eq(USER_ID), eq(7L), request.capture());
        assertThat(request.getValue().plannedAmount()).isEqualByComparingTo("100");
        verify(categoryService, never()).create(any(), any());
    }

    @Test
    void leavesPlanUnchangedWhenZeroOrEmpty() {
        when(categoryService.list(USER_ID)).thenReturn(
                List.of(new CategoryResponse(7L, CategoryType.EXPENSE, "Продукты", null, new BigDecimal("50.00"))));

        importService.importCsv(USER_ID, YEAR, MONTH, csv("Статьи расходов", article("Продукты", "", "0", "5")));
        importService.importCsv(USER_ID, YEAR, MONTH, csv("Статьи расходов", article("Продукты", "", "", "5")));

        verify(categoryService, never()).edit(any(), any(), any());
        verify(categoryService, never()).create(any(), any());
    }

    @Test
    void ignoresDaysBeyondMonthLength() {
        when(categoryService.list(USER_ID))
                .thenReturn(List.of(new CategoryResponse(7L, CategoryType.EXPENSE, "Продукты", null, null)));
        String[] days = new String[29];
        Arrays.fill(days, "");
        days[27] = "7";
        days[28] = "5";

        ImportResultResponse result = importService.importCsv(USER_ID, 2026, 2,
                csv("Статьи расходов", article("Продукты", "", "0", days)));

        assertThat(result.savedOperations()).isEqualTo(1);
        ArgumentCaptor<OperationRequest> operations = ArgumentCaptor.forClass(OperationRequest.class);
        verify(operationService).save(eq(USER_ID), operations.capture());
        assertThat(operations.getValue().date()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void skipsZeroAndEmptyDays() {
        when(categoryService.list(USER_ID))
                .thenReturn(List.of(new CategoryResponse(7L, CategoryType.EXPENSE, "Продукты", null, null)));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv("Статьи расходов", article("Продукты", "", "0", "0", "")));

        assertThat(result.savedOperations()).isZero();
        verify(operationService, never()).save(any(), any());
    }

    @Test
    void rejectsFileWithoutSections() {
        assertThatThrownBy(() -> importService.importCsv(USER_ID, YEAR, MONTH, csv("нет разделов")))
                .isInstanceOf(InvalidImportDataException.class);
        verifyNoInteractions(categoryService, operationService);
    }

    @Test
    void rejectsInvalidMonthOrYear() {
        assertThatThrownBy(() -> importService.importCsv(USER_ID, 2026, 13, csv("Статьи расходов")))
                .isInstanceOf(InvalidImportDataException.class);
        assertThatThrownBy(() -> importService.importCsv(USER_ID, null, 5, csv("Статьи расходов")))
                .isInstanceOf(InvalidImportDataException.class);
        assertThatThrownBy(() -> importService.importCsv(USER_ID, 2026, null, csv("Статьи расходов")))
                .isInstanceOf(InvalidImportDataException.class);
        verifyNoInteractions(categoryService, operationService);
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> importService.importCsv(USER_ID, YEAR, MONTH, null))
                .isInstanceOf(InvalidImportDataException.class);
        verifyNoInteractions(categoryService, operationService);
    }

    private static InputStream csv(String... lines) {
        return new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static String article(String group, String name, String plan, String... days) {
        return group + "," + name + "," + plan + ",,," + String.join(",", days);
    }
}
