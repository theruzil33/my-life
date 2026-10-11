package com.theruzil.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.theruzil.finance.category.CategoryRepository;
import com.theruzil.finance.operation.OperationRepository;
import com.theruzil.finance.operation.InvalidOperationDataException;

@SpringBootTest
class ImportServiceIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OperationRepository operationRepository;

    @Test
    void rollsBackCreatedCategoriesAndOperationsOnError() {
        Long userId = randomUserId();
        String csv = "Статьи расходов\nЕда,Продукты,100,,,1.005";

        assertThatThrownBy(() -> importService.importCsv(userId, 2026, 5, stream(csv)))
                .isInstanceOf(InvalidOperationDataException.class);

        assertThat(categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(userId)).isEmpty();
        assertThat(operationRepository.findByUserIdAndOperationDateOrderById(userId, LocalDate.of(2026, 5, 1)))
                .isEmpty();
    }

    @Test
    void importIsIdempotentAndKeepsUserData() {
        Long userId = randomUserId();
        String csv = "Статьи расходов\nЕда,Продукты,100,,,10,,20";

        importService.importCsv(userId, 2026, 5, stream(csv));
        int categoriesAfterFirst = categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(userId).size();
        int operationsAfterFirst = operationRepository
                .findByUserIdAndOperationDateBetweenOrderById(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
                .size();

        importService.importCsv(userId, 2026, 5, stream(csv));

        assertThat(categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(userId)).hasSize(categoriesAfterFirst);
        assertThat(operationRepository
                .findByUserIdAndOperationDateBetweenOrderById(userId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .hasSize(operationsAfterFirst);
    }

    private static ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
