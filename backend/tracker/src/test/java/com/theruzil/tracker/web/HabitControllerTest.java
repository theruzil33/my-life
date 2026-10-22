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

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.Habit;
import com.theruzil.tracker.habit.HabitFrequency;
import com.theruzil.tracker.habit.HabitRepository;
import com.theruzil.tracker.habit.TimeOfDay;

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
                .content(habitBody("Morning run", TODAY, "DAILY", "HABIT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Morning run"))
                .andExpect(jsonPath("$.frequency").value("DAILY"))
                .andExpect(jsonPath("$.type").value("HABIT"))
                .andExpect(jsonPath("$.startDate").value(TODAY_VALUE))
                .andExpect(jsonPath("$.weekdays").isEmpty());
    }

    @Test
    void createsTaskForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Read a book", TODAY, "DAILY", "TASK")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Read a book"))
                .andExpect(jsonPath("$.type").value("TASK"));
    }

    @Test
    void rejectsHabitWithoutType() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No type\",\"startDate\":\"" + TODAY + "\",\"frequency\":\"DAILY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsHabitWithUnknownType() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Unknown", TODAY, "DAILY", "CHORE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsHabitWithBlankName() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("   ", TODAY, "DAILY", "HABIT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void acceptsHabitWithStartDateInThePast() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Backfilled", TODAY.minusDays(3), "DAILY", "HABIT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(TODAY.minusDays(3).toString()));
    }

    @Test
    void createsPastCompletionsWhenRequested() throws Exception {
        LocalDate start = TODAY.minusDays(3);

        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Backfilled", start, "DAILY", "HABIT", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(start.toString()));

        mockMvc.perform(get("/habits/history").param("from", start.toString()).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].days.length()").value(4))
                .andExpect(jsonPath("$[0].days[0].completed").value(true))
                .andExpect(jsonPath("$[0].days[1].completed").value(true))
                .andExpect(jsonPath("$[0].days[2].completed").value(true))
                .andExpect(jsonPath("$[0].days[3].date").value(TODAY_VALUE))
                .andExpect(jsonPath("$[0].days[3].completed").value(false));
    }

    @Test
    void doesNotCreatePastCompletionsWithoutFlag() throws Exception {
        LocalDate start = TODAY.minusDays(3);

        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Plain", start, "DAILY", "HABIT")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/habits/history").param("from", start.toString()).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].days[0].completed").value(false))
                .andExpect(jsonPath("$[0].days[2].completed").value(false));
    }

    @Test
    void rejectsHabitWithStartDateInTheFuture() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("Early", TODAY.plusDays(1), "DAILY", "HABIT")))
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
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Old", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\",\"startDate\":\"2026-05-10\",\"frequency\":\"SPECIFIC_WEEKDAYS\","
                        + "\"weekdays\":[\"MONDAY\",\"WEDNESDAY\"],\"type\":\"TASK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.startDate").value("2026-05-10"))
                .andExpect(jsonPath("$.frequency").value("SPECIFIC_WEEKDAYS"))
                .andExpect(jsonPath("$.type").value("HABIT"))
                .andExpect(jsonPath("$.weekdays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.weekdays[1]").value("WEDNESDAY"));
    }

    @Test
    void rejectsUpdateWithFutureStartDate() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Old", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("New", TODAY.plusDays(1), "DAILY", "HABIT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUpdateOfForeignHabit() throws Exception {
        Habit habit = seedHabit(userId + 1, ActivityType.HABIT, "Other", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(habitBody("New", TODAY, "DAILY", "HABIT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesOwnHabit() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "daily", TODAY.minusDays(3), HabitFrequency.DAILY,
                Set.of());

        mockMvc.perform(delete("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deletesOwnTask() throws Exception {
        Habit task = seedHabit(userId, ActivityType.TASK, "task", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(delete("/habits/" + task.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/habits").param("type", "TASK").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deletingHabitRemovesItsCompletionsFromHistory() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "daily", TODAY.minusDays(3), HabitFrequency.DAILY,
                Set.of());
        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/habits/history").param("from", TODAY_VALUE).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsDeletionOfForeignHabit() throws Exception {
        Habit habit = seedHabit(userId + 1, ActivityType.HABIT, "Other", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(delete("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsDeletionOfMissingHabit() throws Exception {
        mockMvc.perform(delete("/habits/999999").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeletionWithoutUserIdHeader() throws Exception {
        mockMvc.perform(delete("/habits/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    @Test
    void listsHabitsForTodayWithoutDateParameter() throws Exception {
        seedHabit(userId, ActivityType.HABIT, "daily", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("daily"))
                .andExpect(jsonPath("$[0].type").value("HABIT"))
                .andExpect(jsonPath("$[0].date").value(TODAY_VALUE))
                .andExpect(jsonPath("$[0].completed").value(false));
    }

    @Test
    void listsOnlyRequestedType() throws Exception {
        seedHabit(userId, ActivityType.HABIT, "habit", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());
        seedHabit(userId, ActivityType.TASK, "task", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").param("type", "TASK").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("task"))
                .andExpect(jsonPath("$[0].type").value("TASK"));
    }

    @Test
    void listsBothTypesWhenTypeIsAbsent() throws Exception {
        seedHabit(userId, ActivityType.HABIT, "habit", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());
        seedHabit(userId, ActivityType.TASK, "task", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listsPastDateWithCompletionStatus() throws Exception {
        LocalDate past = TODAY.minusDays(7);
        Habit habit = seedHabit(userId, ActivityType.HABIT, "daily", past.minusDays(5), HabitFrequency.DAILY,
                Set.of());

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
    void listsEmptyBeforeEarliestHabit() throws Exception {
        seedHabit(userId, ActivityType.HABIT, "daily", TODAY.minusDays(2), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").param("date", TODAY.minusDays(3).toString())
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void historyRangeBelowEarliestHabitIsAllowed() throws Exception {
        seedHabit(userId, ActivityType.HABIT, "daily", TODAY.minusDays(1), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits/history").param("from", TODAY.minusDays(10).toString())
                .param("to", TODAY_VALUE).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].days.length()").value(2))
                .andExpect(jsonPath("$[0].days[0].date").value(TODAY.minusDays(1).toString()));
    }

    @Test
    void doesNotListHabitsOfOtherUsers() throws Exception {
        seedHabit(userId + 1, ActivityType.HABIT, "other", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsHistoryForRange() throws Exception {
        LocalDate from = TODAY.minusDays(2);
        seedHabit(userId, ActivityType.HABIT, "daily", from, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits/history").param("from", from.toString()).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("HABIT"))
                .andExpect(jsonPath("$[0].days.length()").value(3))
                .andExpect(jsonPath("$[0].days[0].date").value(from.toString()))
                .andExpect(jsonPath("$[0].days[0].completed").value(false))
                .andExpect(jsonPath("$[0].days[2].date").value(TODAY_VALUE));
    }

    @Test
    void historyIncludesBothTypes() throws Exception {
        LocalDate from = TODAY.minusDays(1);
        seedHabit(userId, ActivityType.HABIT, "habit", from, HabitFrequency.DAILY, Set.of());
        seedHabit(userId, ActivityType.TASK, "task", from, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits/history").param("from", from.toString()).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("HABIT"))
                .andExpect(jsonPath("$[1].type").value("TASK"));
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
        seedHabit(userId + 1, ActivityType.HABIT, "other", from, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(get("/habits/history").param("from", from.toString()).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void marksAndUnmarksCompletion() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "daily", TODAY.minusDays(3), HabitFrequency.DAILY,
                Set.of());

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
    void marksTaskCompletion() throws Exception {
        Habit task = seedHabit(userId, ActivityType.TASK, "task", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + task.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.type").value("TASK"));
    }

    @Test
    void rejectsCompletionInTheFuture() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "daily", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY.plusDays(1))
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsCompletionOnUnscheduledWeekday() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "weekly", TODAY.minusDays(10),
                HabitFrequency.SPECIFIC_WEEKDAYS, Set.of(TODAY.getDayOfWeek().plus(1)));

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCompletionOfForeignHabit() throws Exception {
        Habit habit = seedHabit(userId + 1, ActivityType.HABIT, "other", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound());
    }


    @Test
    void createsHabitWithTimeOfDay() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Teeth\",\"startDate\":\"" + TODAY
                        + "\",\"frequency\":\"DAILY\",\"type\":\"HABIT\","
                        + "\"timeOfDay\":[\"MORNING\",\"EVENING\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeOfDay[0]").value("MORNING"))
                .andExpect(jsonPath("$.timeOfDay[1]").value("EVENING"));
    }

    @Test
    void rejectsDuplicateTimeOfDay() throws Exception {
        mockMvc.perform(post("/habits").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Teeth\",\"startDate\":\"" + TODAY
                        + "\",\"frequency\":\"DAILY\",\"type\":\"HABIT\","
                        + "\"timeOfDay\":[\"MORNING\",\"MORNING\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updatesTimeOfDay() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Teeth", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Teeth\",\"startDate\":\"" + TODAY
                        + "\",\"frequency\":\"DAILY\",\"type\":\"HABIT\","
                        + "\"timeOfDay\":[\"MORNING\",\"EVENING\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeOfDay[0]").value("MORNING"))
                .andExpect(jsonPath("$.timeOfDay[1]").value("EVENING"));
    }

    @Test
    void listsCompletionStatusForEachTimeOfDay() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Teeth", TODAY, HabitFrequency.DAILY, Set.of(),
                Set.of(TimeOfDay.MORNING, TimeOfDay.EVENING));

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .param("timeOfDay", "MORNING")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completions[0].timeOfDay").value("MORNING"))
                .andExpect(jsonPath("$.completions[0].completed").value(true))
                .andExpect(jsonPath("$.completions[1].timeOfDay").value("EVENING"))
                .andExpect(jsonPath("$.completions[1].completed").value(false));

        mockMvc.perform(get("/habits").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeOfDay[0]").value("MORNING"))
                .andExpect(jsonPath("$[0].completions[0].completed").value(true))
                .andExpect(jsonPath("$[0].completions[1].completed").value(false));
    }

    @Test
    void unmarksSingleTimeOfDay() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Teeth", TODAY, HabitFrequency.DAILY, Set.of(),
                Set.of(TimeOfDay.MORNING, TimeOfDay.EVENING));

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .param("timeOfDay", "MORNING")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/habits/" + habit.getId() + "/completions/" + TODAY)
                .param("timeOfDay", "MORNING")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completions[0].completed").value(false))
                .andExpect(jsonPath("$.completions[1].completed").value(false));
    }

    @Test
    void rejectsMarkWithoutTimeOfDayForActivityWithTimeOfDay() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Teeth", TODAY, HabitFrequency.DAILY, Set.of(),
                Set.of(TimeOfDay.MORNING));

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsTimeOfDayForActivityWithoutTimeOfDay() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Teeth", TODAY, HabitFrequency.DAILY, Set.of());

        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .param("timeOfDay", "MORNING")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void historyReturnsEntriesForEachTimeOfDay() throws Exception {
        Habit habit = seedHabit(userId, ActivityType.HABIT, "Teeth", TODAY, HabitFrequency.DAILY, Set.of(),
                Set.of(TimeOfDay.MORNING, TimeOfDay.EVENING));
        mockMvc.perform(put("/habits/" + habit.getId() + "/completions/" + TODAY)
                .param("timeOfDay", "MORNING")
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/habits/history").param("from", TODAY_VALUE).param("to", TODAY_VALUE)
                .header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeOfDay[0]").value("MORNING"))
                .andExpect(jsonPath("$[0].days.length()").value(2))
                .andExpect(jsonPath("$[0].days[0].timeOfDay").value("MORNING"))
                .andExpect(jsonPath("$[0].days[0].completed").value(true))
                .andExpect(jsonPath("$[0].days[1].timeOfDay").value("EVENING"))
                .andExpect(jsonPath("$[0].days[1].completed").value(false));
    }

    private Habit seedHabit(Long ownerId, ActivityType type, String name, LocalDate startDate,
            HabitFrequency frequency, Set<DayOfWeek> weekdays) {
        return seedHabit(ownerId, type, name, startDate, frequency, weekdays, Set.of());
    }

    private Habit seedHabit(Long ownerId, ActivityType type, String name, LocalDate startDate,
            HabitFrequency frequency, Set<DayOfWeek> weekdays, Set<TimeOfDay> timeOfDay) {
        return habitRepository.saveAndFlush(
                new Habit(ownerId, type, name, startDate, frequency, weekdays, timeOfDay, NOW));
    }

    private String habitBody(String name, LocalDate startDate, String frequency, String type) {
        return "{\"name\":\"" + name + "\",\"startDate\":\"" + startDate + "\",\"frequency\":\"" + frequency
                + "\",\"type\":\"" + type + "\"}";
    }

    private String habitBody(String name, LocalDate startDate, String frequency, String type,
            boolean markPastCompleted) {
        return "{\"name\":\"" + name + "\",\"startDate\":\"" + startDate + "\",\"frequency\":\"" + frequency
                + "\",\"type\":\"" + type + "\",\"markPastCompleted\":" + markPastCompleted + "}";
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }
}
