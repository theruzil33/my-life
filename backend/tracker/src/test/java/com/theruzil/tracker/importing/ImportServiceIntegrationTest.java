package com.theruzil.tracker.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.Habit;
import com.theruzil.tracker.habit.HabitCompletionRepository;
import com.theruzil.tracker.habit.HabitRepository;
import com.theruzil.tracker.habit.HabitService;
import com.theruzil.tracker.habit.TimeOfDay;
import com.theruzil.tracker.habit.dto.HabitHistoryResponse;
import com.theruzil.tracker.habit.dto.HabitOnDateResponse;
import com.theruzil.tracker.habit.dto.TimeOfDayCompletion;

@SpringBootTest
class ImportServiceIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private HabitCompletionRepository completionRepository;

    @Autowired
    private HabitService habitService;

    @Test
    void importIsIdempotentAndKeepsUserData() {
        Long userId = randomUserId();
        String csv = "Продукты,,Задача,1,,1";

        importService.importCsv(userId, 2026, 5, stream(csv));
        int completionsAfterFirst = completionsOf(userId, ActivityType.TASK, "Продукты");

        importService.importCsv(userId, 2026, 5, stream(csv));

        assertThat(habitsOf(userId, ActivityType.TASK, "Продукты")).hasSize(1);
        assertThat(completionsOf(userId, ActivityType.TASK, "Продукты")).isEqualTo(completionsAfterFirst);
    }


    @Test
    void mergesMorningAndEveningIntoOneActivityWithSeparateCompletions() {
        Long userId = randomUserId();
        String csv = String.join("\n",
                "Чистил зубы,Утром,Привычка,1",
                ",Вечером,Привычка,1");

        importService.importCsv(userId, 2026, 5, stream(csv));

        List<Habit> habits = habitsOf(userId, ActivityType.HABIT, "Чистил зубы");
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).getTimeOfDay()).containsExactlyInAnyOrder(TimeOfDay.MORNING, TimeOfDay.EVENING);
        assertThat(completionsOf(userId, ActivityType.HABIT, "Чистил зубы")).isEqualTo(2);
    }

    @Test
    void ignoresRowWithoutVariantWhenActivityHasTimeOfDay() {
        Long userId = randomUserId();
        String csv = String.join("\n",
                "Чистил зубы,Утром,Привычка,1",
                "Чистил зубы,Вечером,Привычка,1",
                "Чистил зубы,,Привычка,1");

        importService.importCsv(userId, 2026, 5, stream(csv));

        assertThat(completionsOf(userId, ActivityType.HABIT, "Чистил зубы")).isEqualTo(2);
    }

    @Test
    void repeatedImportDoesNotDuplicateTimeOfDayCompletions() {
        Long userId = randomUserId();
        String csv = String.join("\n",
                "Чистил зубы,Утром,Привычка,1",
                ",Вечером,Привычка,1");

        importService.importCsv(userId, 2026, 5, stream(csv));
        importService.importCsv(userId, 2026, 5, stream(csv));

        assertThat(habitsOf(userId, ActivityType.HABIT, "Чистил зубы")).hasSize(1);
        assertThat(completionsOf(userId, ActivityType.HABIT, "Чистил зубы")).isEqualTo(2);
    }

    @Test
    void createsActivityStartingOnFirstDayOfMonth() {
        Long userId = randomUserId();

        importService.importCsv(userId, 2026, 5, stream("Продукты,,Задача,1,,1"));

        List<Habit> habits = habitsOf(userId, ActivityType.TASK, "Продукты");
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(completionsOf(userId, ActivityType.TASK, "Продукты")).isEqualTo(2);
    }

    @Test
    void rollsBackCreatedActivitiesOnError() {
        Long userId = randomUserId();
        String tooLongName = "A".repeat(250);
        String csv = String.join("\n", "Продукты,,Задача,1", tooLongName + ",,Задача,1");

        assertThatThrownBy(() -> importService.importCsv(userId, 2026, 5, stream(csv)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(habitsOf(userId, ActivityType.TASK, "Продукты")).isEmpty();
    }


    @Test
    void importedTimeOfDayIsVisibleInListAndHistory() {
        Long userId = randomUserId();
        YearMonth month = YearMonth.now().minusMonths(1);
        LocalDate date = month.atDay(1);
        String csv = String.join("\n",
                "Чистил зубы,Утром,Привычка,1",
                ",Вечером,Привычка,1");

        importService.importCsv(userId, month.getYear(), month.getMonthValue(), stream(csv));

        HabitOnDateResponse onDate = habitService.listOnDate(userId, date, null).get(0);
        assertThat(onDate.name()).isEqualTo("Чистил зубы");
        assertThat(onDate.timeOfDay()).containsExactly(TimeOfDay.MORNING, TimeOfDay.EVENING);
        assertThat(onDate.completions()).containsExactly(
                new TimeOfDayCompletion(TimeOfDay.MORNING, true),
                new TimeOfDayCompletion(TimeOfDay.EVENING, true));
        assertThat(onDate.completed()).isTrue();

        HabitHistoryResponse history = habitService.history(userId, date, date).get(0);
        assertThat(history.days()).extracting(entry -> entry.timeOfDay())
                .containsExactly(TimeOfDay.MORNING, TimeOfDay.EVENING);
        assertThat(history.days()).allMatch(entry -> entry.completed());
    }

    private List<Habit> habitsOf(Long userId, ActivityType type, String name) {
        return habitRepository.findByUserIdAndTypeAndNameOrderById(userId, type, name);
    }

    private int completionsOf(Long userId, ActivityType type, String name) {
        List<Long> habitIds = habitsOf(userId, type, name).stream().map(Habit::getId).toList();
        if (habitIds.isEmpty()) {
            return 0;
        }
        return completionRepository
                .findByHabitIdInAndCompletedOnBetween(habitIds, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
                .size();
    }

    private static ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
