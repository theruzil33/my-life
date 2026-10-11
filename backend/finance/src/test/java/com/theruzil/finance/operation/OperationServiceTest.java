package com.theruzil.finance.operation;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.finance.category.Category;
import com.theruzil.finance.category.CategoryNotFoundException;
import com.theruzil.finance.category.CategoryRepository;
import com.theruzil.finance.category.CategoryType;
import com.theruzil.finance.operation.dto.OperationRequest;
import com.theruzil.finance.operation.dto.OperationResponse;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long CATEGORY_ID = 7L;
    private static final LocalDate DATE = LocalDate.parse("2026-05-13");
    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Mock
    private OperationRepository operationRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private OperationService operationService;

    @BeforeEach
    void setUp() {
        operationService = new OperationService(operationRepository, categoryRepository);
    }

    @Test
    void saveCreatesOperationForUserAndReturnsResponse() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category()));
        when(operationRepository.findByCategoryIdAndOperationDate(CATEGORY_ID, DATE)).thenReturn(Optional.empty());
        when(operationRepository.save(any(Operation.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        OperationResponse response = operationService.save(USER_ID, new OperationRequest(CATEGORY_ID, DATE, new BigDecimal("100.00")));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(response.date()).isEqualTo(DATE);
        assertThat(response.amount()).isEqualByComparingTo("100.00");

        ArgumentCaptor<Operation> captor = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getCategoryId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    void saveUpdatesExistingOperationWithoutCreatingSecond() {
        Operation existing = operationWithId(5L, USER_ID, CATEGORY_ID, DATE, "50.00");
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category()));
        when(operationRepository.findByCategoryIdAndOperationDate(CATEGORY_ID, DATE)).thenReturn(Optional.of(existing));
        when(operationRepository.save(any(Operation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationResponse response = operationService.save(USER_ID, new OperationRequest(CATEGORY_ID, DATE, new BigDecimal("80.00")));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.amount()).isEqualByComparingTo("80.00");
        assertThat(existing.getAmount()).isEqualByComparingTo("80.00");
        verify(operationRepository, org.mockito.Mockito.times(1)).save(any(Operation.class));
    }

    @Test
    void saveAllowsPastAndFutureDates() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category()));
        when(operationRepository.findByCategoryIdAndOperationDate(any(), any())).thenReturn(Optional.empty());
        when(operationRepository.save(any(Operation.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 9L));

        OperationResponse past = operationService.save(USER_ID,
                new OperationRequest(CATEGORY_ID, LocalDate.parse("2020-01-01"), new BigDecimal("10.00")));
        OperationResponse future = operationService.save(USER_ID,
                new OperationRequest(CATEGORY_ID, LocalDate.parse("2030-01-01"), new BigDecimal("10.00")));

        assertThat(past.date()).isEqualTo(LocalDate.parse("2020-01-01"));
        assertThat(future.date()).isEqualTo(LocalDate.parse("2030-01-01"));
    }

    @Test
    void saveRejectsMissingCategory() {
        assertThatThrownBy(() -> operationService.save(USER_ID, new OperationRequest(null, DATE, new BigDecimal("10"))))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository, categoryRepository);
    }

    @Test
    void saveRejectsMissingDate() {
        assertThatThrownBy(() -> operationService.save(USER_ID, new OperationRequest(CATEGORY_ID, null, new BigDecimal("10"))))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository, categoryRepository);
    }

    @Test
    void saveRejectsMissingAmount() {
        assertThatThrownBy(() -> operationService.save(USER_ID, new OperationRequest(CATEGORY_ID, DATE, null)))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository, categoryRepository);
    }

    @Test
    void saveRejectsNonPositiveAmount() {
        assertThatThrownBy(
                () -> operationService.save(USER_ID, new OperationRequest(CATEGORY_ID, DATE, BigDecimal.ZERO)))
                .isInstanceOf(InvalidOperationDataException.class);
        assertThatThrownBy(
                () -> operationService.save(USER_ID, new OperationRequest(CATEGORY_ID, DATE, new BigDecimal("-5"))))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository, categoryRepository);
    }

    @Test
    void saveRejectsAmountWithMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> operationService.save(USER_ID,
                new OperationRequest(CATEGORY_ID, DATE, new BigDecimal("10.123"))))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository, categoryRepository);
    }

    @Test
    void saveRejectsForeignOrUnknownCategory() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operationService.save(USER_ID, new OperationRequest(CATEGORY_ID, DATE, new BigDecimal("10"))))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(operationRepository, never()).save(any(Operation.class));
    }

    @Test
    void listByDateReturnsOperationsOfUser() {
        when(operationRepository.findByUserIdAndOperationDateOrderById(USER_ID, DATE)).thenReturn(List.of(
                operationWithId(1L, USER_ID, CATEGORY_ID, DATE, "50.00"),
                operationWithId(2L, USER_ID, CATEGORY_ID, DATE, "20.00")));

        List<OperationResponse> operations = operationService.listByDate(USER_ID, DATE);

        assertThat(operations).extracting(OperationResponse::id, OperationResponse::amount)
                .containsExactly(tuple(1L, new BigDecimal("50.00")), tuple(2L, new BigDecimal("20.00")));
    }

    @Test
    void listByDateRejectsMissingDate() {
        assertThatThrownBy(() -> operationService.listByDate(USER_ID, null))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository);
    }

    @Test
    void listByRangeReturnsOperationsOfUser() {
        LocalDate from = LocalDate.parse("2026-05-01");
        LocalDate to = LocalDate.parse("2026-05-31");
        when(operationRepository.findByUserIdAndOperationDateBetweenOrderById(USER_ID, from, to))
                .thenReturn(List.of(operationWithId(1L, USER_ID, CATEGORY_ID, DATE, "50.00")));

        List<OperationResponse> operations = operationService.listByRange(USER_ID, from, to);

        assertThat(operations).extracting(OperationResponse::id).containsExactly(1L);
    }

    @Test
    void listByRangeRejectsMissingBounds() {
        assertThatThrownBy(() -> operationService.listByRange(USER_ID, null, DATE))
                .isInstanceOf(InvalidOperationDataException.class);
        assertThatThrownBy(() -> operationService.listByRange(USER_ID, DATE, null))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository);
    }

    @Test
    void listByRangeRejectsStartAfterEnd() {
        assertThatThrownBy(() -> operationService.listByRange(USER_ID, LocalDate.parse("2026-05-31"), LocalDate.parse("2026-05-01")))
                .isInstanceOf(InvalidOperationDataException.class);
        verifyNoInteractions(operationRepository);
    }

    @Test
    void deleteRemovesOwnOperation() {
        Operation operation = operationWithId(5L, USER_ID, CATEGORY_ID, DATE, "50.00");
        when(operationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(operation));

        operationService.delete(USER_ID, 5L);

        verify(operationRepository).delete(operation);
    }

    @Test
    void deleteRejectsUnknownOrForeignOperation() {
        when(operationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operationService.delete(USER_ID, 5L))
                .isInstanceOf(OperationNotFoundException.class);
        verify(operationRepository, never()).delete(any(Operation.class));
    }

    private static Category category() {
        Category category = new Category(USER_ID, CategoryType.INCOME, "Зарплата", null, NOW);
        try {
            Field field = Category.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(category, CATEGORY_ID);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return category;
    }

    private static Operation operationWithId(Long id, Long userId, Long categoryId, LocalDate date, String amount) {
        Operation operation = new Operation(userId, categoryId, date, new BigDecimal(amount), NOW);
        setId(operation, id);
        return operation;
    }

    private static Operation withId(Operation operation, Long id) {
        setId(operation, id);
        return operation;
    }

    private static void setId(Operation operation, Long id) {
        try {
            Field field = Operation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(operation, id);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
