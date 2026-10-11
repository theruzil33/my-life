package com.theruzil.finance.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CategoryControllerTest {

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
    void createsAndListsCategoriesForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INCOME\",\"name\":\"Зарплата\",\"group\":\"Работа\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.name").value("Зарплата"))
                .andExpect(jsonPath("$.group").value("Работа"));

        mockMvc.perform(get("/categories").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Зарплата"))
                .andExpect(jsonPath("$[0].group").value("Работа"));
    }

    @Test
    void createsCategoryWithoutGroup() throws Exception {
        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"name\":\"Продукты\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.group").doesNotExist());
    }

    @Test
    void listsIncomeBeforeExpense() throws Exception {
        categoryRepository.save(new Category(userId, CategoryType.EXPENSE, "Продукты", null, NOW));
        categoryRepository.save(new Category(userId, CategoryType.INCOME, "Зарплата", "Работа", NOW));

        mockMvc.perform(get("/categories").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("INCOME"))
                .andExpect(jsonPath("$[1].type").value("EXPENSE"));
    }

    @Test
    void editsNameAndGroupKeepingType() throws Exception {
        Category category = categoryRepository.save(
                new Category(userId, CategoryType.INCOME, "Зарплата", "Работа", NOW));

        mockMvc.perform(put("/categories/" + category.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"name\":\"Премия\",\"group\":\"Бонусы\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.name").value("Премия"))
                .andExpect(jsonPath("$.group").value("Бонусы"));
    }

    @Test
    void editsCategoryClearingGroup() throws Exception {
        Category category = categoryRepository.save(
                new Category(userId, CategoryType.INCOME, "Зарплата", "Работа", NOW));

        mockMvc.perform(put("/categories/" + category.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Зарплата\",\"group\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").doesNotExist());
    }

    @Test
    void deletesCategory() throws Exception {
        Category category = categoryRepository.save(new Category(userId, CategoryType.INCOME, "Зарплата", null, NOW));

        mockMvc.perform(delete("/categories/" + category.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/categories").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsDeletingCategoryWithOperations() throws Exception {
        Category category = categoryRepository.save(new Category(userId, CategoryType.EXPENSE, "Продукты", null, NOW));
        operationRepository.save(new Operation(userId, category.getId(), java.time.LocalDate.parse("2026-05-13"),
                new java.math.BigDecimal("100.00"), NOW));

        mockMvc.perform(delete("/categories/" + category.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/categories").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INCOME\",\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsUnknownType() throws Exception {
        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SALARY\",\"name\":\"Зарплата\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsEditingForeignCategory() throws Exception {
        Category foreign = categoryRepository.save(
                new Category(randomUserId(), CategoryType.INCOME, "Чужая", null, NOW));

        mockMvc.perform(put("/categories/" + foreign.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Новая\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsDeletingForeignCategory() throws Exception {
        Category foreign = categoryRepository.save(
                new Category(randomUserId(), CategoryType.INCOME, "Чужая", null, NOW));

        mockMvc.perform(delete("/categories/" + foreign.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsDeletingMissingCategory() throws Exception {
        mockMvc.perform(delete("/categories/999999").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
