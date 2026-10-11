package com.theruzil.finance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.finance.category.Category;
import com.theruzil.finance.category.CategoryRepository;
import com.theruzil.finance.category.CategoryType;
import com.theruzil.finance.operation.Operation;
import com.theruzil.finance.operation.OperationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BalanceControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OperationRepository operationRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = randomUserId();
    }

    @Test
    void returnsDefaultBalanceWithoutStartingBalance() throws Exception {
        mockMvc.perform(get("/balance").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(0))
                .andExpect(jsonPath("$.amount").value(0));
    }

    @Test
    void computesBalanceWithStartingBalanceAndOperations() throws Exception {
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":1000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(1000.00));

        record("Зарплата", CategoryType.INCOME, new BigDecimal("500.00"), LocalDate.now());
        record("Еда", CategoryType.EXPENSE, new BigDecimal("200.00"), LocalDate.now());

        mockMvc.perform(get("/balance").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(1000.00))
                .andExpect(jsonPath("$.amount").value(1300.00));
    }

    @Test
    void countsFutureOperationsInBalance() throws Exception {
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":1000.00}"))
                .andExpect(status().isOk());

        record("Аренда", CategoryType.EXPENSE, new BigDecimal("300.00"), LocalDate.now().plusDays(10));

        mockMvc.perform(get("/balance").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(700.00));
    }

    @Test
    void updateAllowsZeroAndNegative() throws Exception {
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(0));

        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":-250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(-250.00));
    }

    @Test
    void updateReplacesPreviousValue() throws Exception {
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":100.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":200.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(200.00));
    }

    @Test
    void rejectsMissingValue() throws Exception {
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsAmountWithMoreThanTwoDecimalPlaces() throws Exception {
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":10.123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateDoesNotChangeOperations() throws Exception {
        record("Еда", CategoryType.EXPENSE, new BigDecimal("200.00"), LocalDate.now());

        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":1000.00}"))
                .andExpect(status().isOk());

        List<Operation> operations = operationRepository.findByUserIdAndOperationDateOrderById(userId, LocalDate.now());
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void isolatesBalanceBetweenUsers() throws Exception {
        Long otherUserId = randomUserId();
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":1000.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/balance").header(UserIdFilter.USER_ID_HEADER, otherUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startingBalance\":9999.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/balance").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(1000.00));
    }

    @Test
    void rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    private void record(String categoryName, CategoryType type, BigDecimal amount, LocalDate date) {
        Category category = categoryRepository.save(new Category(userId, type, categoryName, null, NOW));
        operationRepository.save(new Operation(userId, category.getId(), date, amount, NOW));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
