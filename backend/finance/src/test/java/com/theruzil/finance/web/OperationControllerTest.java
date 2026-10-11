package com.theruzil.finance.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
class OperationControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

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
    void savesAndListsOperationForDate() throws Exception {
        mockMvc.perform(put("/operations").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId + ",\"date\":\"2026-05-13\",\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.date").value("2026-05-13"))
                .andExpect(jsonPath("$.amount").value(100.00));

        mockMvc.perform(get("/operations").header(UserIdFilter.USER_ID_HEADER, userId).param("date", "2026-05-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(100.00));
    }

    @Test
    void savingSameCategoryAndDateUpdatesWithoutDuplicating() throws Exception {
        mockMvc.perform(put("/operations").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId + ",\"date\":\"2026-05-13\",\"amount\":100.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/operations").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId + ",\"date\":\"2026-05-13\",\"amount\":250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250.00));

        mockMvc.perform(get("/operations").header(UserIdFilter.USER_ID_HEADER, userId).param("date", "2026-05-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(250.00));
    }

    @Test
    void listsOperationsForRange() throws Exception {
        operationRepository.save(new Operation(userId, categoryId, java.time.LocalDate.parse("2026-05-15"),
                new java.math.BigDecimal("100.00"), NOW));

        mockMvc.perform(get("/operations").header(UserIdFilter.USER_ID_HEADER, userId)
                .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-05-15"));
    }

    @Test
    void deletesOperation() throws Exception {
        Operation operation = operationRepository.save(new Operation(userId, categoryId,
                java.time.LocalDate.parse("2026-05-13"), new java.math.BigDecimal("100.00"), NOW));

        mockMvc.perform(delete("/operations/" + operation.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/operations").header(UserIdFilter.USER_ID_HEADER, userId).param("date", "2026-05-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsMissingAmount() throws Exception {
        mockMvc.perform(put("/operations").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId + ",\"date\":\"2026-05-13\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(put("/operations").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId + ",\"date\":\"2026-05-13\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsForeignCategory() throws Exception {
        Long foreignCategory = categoryRepository
                .save(new Category(randomUserId(), CategoryType.INCOME, "Чужая", null, NOW)).getId();

        mockMvc.perform(put("/operations").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + foreignCategory + ",\"date\":\"2026-05-13\",\"amount\":100.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsDeletingMissingOperation() throws Exception {
        mockMvc.perform(delete("/operations/999999").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/operations").param("date", "2026-05-13"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
