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
    void createsCategoryWithPlannedAmount() throws Exception {
        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"name\":\"Продукты\",\"plannedAmount\":15000.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedAmount")
                        .value(org.hamcrest.Matchers.closeTo(15000.00, 0.001)));

        mockMvc.perform(get("/categories").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plannedAmount")
                        .value(org.hamcrest.Matchers.closeTo(15000.00, 0.001)));
    }

    @Test
    void createsCategoryWithoutPlannedAmount() throws Exception {
        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"name\":\"Продукты\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedAmount").doesNotExist());
    }

    @Test
    void rejectsNonPositivePlannedAmount() throws Exception {
        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"name\":\"Продукты\",\"plannedAmount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/categories").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"name\":\"Продукты\",\"plannedAmount\":-10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void editsPlannedAmount() throws Exception {
        Category category = categoryRepository.save(new Category(userId, CategoryType.EXPENSE, "Продукты", null, NOW));

        mockMvc.perform(put("/categories/" + category.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Продукты\",\"plannedAmount\":250.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedAmount")
                        .value(org.hamcrest.Matchers.closeTo(250.50, 0.001)));
    }

    @Test
    void editsCategoryClearingPlannedAmount() throws Exception {
        Category category = categoryRepository.save(
                new Category(userId, CategoryType.EXPENSE, "Продукты", null, new java.math.BigDecimal("250.50"), NOW));

        mockMvc.perform(put("/categories/" + category.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Продукты\",\"plannedAmount\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedAmount").doesNotExist());
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
    void reordersCategoriesOfTypeAndReflectsInList() throws Exception {
        Category first = categoryRepository.save(
                new Category(userId, CategoryType.INCOME, "A", null, null, 1, NOW));
        Category second = categoryRepository.save(
                new Category(userId, CategoryType.INCOME, "B", null, null, 2, NOW));

        mockMvc.perform(put("/categories/order").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INCOME\",\"categoryIds\":[" + second.getId() + "," + first.getId() + "]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/categories").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(first.getId()));
    }

    @Test
    void rejectsReorderingWithForeignCategory() throws Exception {
        Category foreign = categoryRepository.save(
                new Category(randomUserId(), CategoryType.INCOME, "Чужая", null, null, 1, NOW));

        mockMvc.perform(put("/categories/order").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INCOME\",\"categoryIds\":[" + foreign.getId() + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsReorderingWithWrongTypeDuplicateOrIncompleteSet() throws Exception {
        Category income = categoryRepository.save(
                new Category(userId, CategoryType.INCOME, "A", null, null, 1, NOW));
        categoryRepository.save(new Category(userId, CategoryType.INCOME, "B", null, null, 2, NOW));
        Category expense = categoryRepository.save(
                new Category(userId, CategoryType.EXPENSE, "Продукты", null, null, 1, NOW));

        mockMvc.perform(put("/categories/order").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INCOME\",\"categoryIds\":[" + expense.getId() + "]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/categories/order").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INCOME\",\"categoryIds\":[" + income.getId() + "," + income.getId() + "]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/categories/order").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INCOME\",\"categoryIds\":[" + income.getId() + "]}"))
                .andExpect(status().isBadRequest());
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
