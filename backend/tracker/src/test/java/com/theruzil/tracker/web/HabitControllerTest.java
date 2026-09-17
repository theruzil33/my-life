package com.theruzil.tracker.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.tracker.habit.Habit;
import com.theruzil.tracker.habit.HabitFrequency;
import com.theruzil.tracker.habit.HabitRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HabitControllerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 13);
    private static final String TODAY_VALUE = "2026-05-13";

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZONE);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HabitRepository habitRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = randomUserId();
    }

    @Test
    void createsHabitForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Morning run", TODAY, "DAILY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Morning run"))
                .andExpect(jsonPath("$.frequency").value("DAILY"))
                .andExpect(jsonPath("$.startDate").value(TODAY_VALUE))
                .andExpect(jsonPath("$.weekdays").isEmpty());
    }

    @Test
    void rejectsHabitWithBlankName() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("   ", TODAY, "DAILY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsHabitWithStartDateInThePast() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Late", TODAY.minusDays(1), "DAILY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/habits"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    @Test
    void updatesHabit() throws Exception {
        Habit habit = seedHabit(userId, "Old", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\",\"startDate\":\"2026-05-20\",\"frequency\":\"SPECIFIC_WEEKDAYS\","
                        + "\"weekdays\":[\"MONDAY\",\"WEDNESDAY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.startDate").value("2026-05-20"))
                .andExpect(jsonPath("$.frequency").value("SPECIFIC_WEEKDAYS"))
                .andExpect(jsonPath("$.weekdays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.weekdays[1]").value("WEDNESDAY"));
    }

    @Test
    void rejectsUpdateWithPastStartDate() throws Exception {
        Habit habit = seedHabit(userId, "Old", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("New", TODAY.minusDays(1), "DAILY")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUpdateOfForeignHabit() throws Exception {
        Habit habit = seedHabit(userId + 1, "Other", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("New", TODAY, "DAILY")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsHabitsForTodayWithoutDateParameter() throws Exception {
        seedHabit(userId, "daily", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("daily"))
                .andExpect(jsonPath("$[0].date").value(TODAY_VALUE))
                .andExpect(jsonPath("$[0].completed").value(false));
    }

    @Test
    void listsPastDateWithCompletionStatus() throws Exception {
        LocalDate past = TODAY.minusDays(7);
        Habit habit = seedHabit(userId, "daily", past.minusDays(5), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + past)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/habits").param("date", past.toString())
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value(past.toString()))
                .andExpect(jsonPath("$[0].completed").value(true));
    }

    @Test
    void rejectsListDateBeforeEarliestHabit() throws Exception {
        seedHabit(userId, "daily", TODAY.minusDays(2), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").param("date", TODAY.minusDays(3).toString())
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void doesNotListHabitsOfOtherUsers() throws Exception {
        seedHabit(userId + 1, "other", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsHistoryForRange() throws Exception {
        LocalDate from = TODAY.minusDays(2);
        seedHabit(userId, "daily", from, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits/history").param("from", from.toString()).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].days.length()").value(3))
                .andExpect(jsonPath("$[0].days[0].date").value(from.toString()))
                .andExpect(jsonPath("$[0].days[0].completed").value(false))
                .andExpect(jsonPath("$[0].days[2].date").value(TODAY_VALUE));
    }

    @Test
    void rejectsHistoryRangeWithStartAfterEnd() throws Exception {
        mockMvc.perform(get("/habits/history").param("from", TODAY_VALUE)
                .param("to", TODAY.minusDays(1).toString())
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void historyDoesNotIncludeHabitsOfOtherUsers() throws Exception {
        LocalDate from = TODAY.minusDays(1);
        seedHabit(userId + 1, "other", from, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits/history").param("from", from.toString()).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void marksAndUnmarksCompletion() throws Exception {
        Habit habit = seedHabit(userId, "daily", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(TODAY_VALUE))
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(delete("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void rejectsCompletionInTheFuture() throws Exception {
        Habit habit = seedHabit(userId, "daily", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY.plusDays(1))
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsCompletionOnUnscheduledWeekday() throws Exception {
        Habit habit = seedHabit(userId, "weekly", TODAY.minusDays(10), HabitFrequency.SPECIFIC_WEEKDAYS,
                Set.of(TODAY.getDayOfWeek().plus(1)));

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCompletionOfForeignHabit() throws Exception {
        Habit habit = seedHabit(userId + 1, "other", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound());
    }

    private Habit seedHabit(Long ownerId, String name, LocalDate startDate, HabitFrequency frequency,
            Set<DayOfWeek> weekdays) {
        return habitRepository.saveAndFlush(new Habit(ownerId, name, startDate, frequency, weekdays, NOW));
    }

    private String habitBody(String name, LocalDate startDate, String frequency) {
        return "{\"name\":\"" + name + "\",\"startDate\":\"" + startDate + "\",\"frequency\":\"" + frequency + "\"}";
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }
}
