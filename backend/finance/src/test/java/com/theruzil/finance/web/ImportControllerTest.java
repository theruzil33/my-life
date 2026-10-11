package com.theruzil.finance.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImportControllerTest {

    private static final String CSV = "Статьи расходов\nЕда,Продукты,100,,,10,,20";

    @Autowired
    private MockMvc mockMvc;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = randomUserId();
    }

    @Test
    void importsCsvAndReturnsSummary() throws Exception {
        mockMvc.perform(multipart("/imports").file(csvFile()).param("year", "2026").param("month", "5")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCategories").value(1))
                .andExpect(jsonPath("$.savedOperations").value(2));
    }

    @Test
    void rejectsMissingFile() throws Exception {
        mockMvc.perform(multipart("/imports").param("year", "2026").param("month", "5")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingMonthOrYear() throws Exception {
        mockMvc.perform(multipart("/imports").file(csvFile()).param("year", "2026")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/imports").file(csvFile()).param("month", "5")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidMonth() throws Exception {
        mockMvc.perform(multipart("/imports").file(csvFile()).param("year", "2026").param("month", "13")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(multipart("/imports").file(csvFile()).param("year", "2026").param("month", "5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    private static MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "Деньги.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
