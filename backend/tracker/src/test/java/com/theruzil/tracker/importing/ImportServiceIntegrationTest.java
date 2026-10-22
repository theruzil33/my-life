package com.theruzil.tracker.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

@SpringBootTest
class ImportServiceIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private HabitCompletionRepository completionRepository;

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
