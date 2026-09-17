package com.theruzil.tracker.habit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class HabitRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 13);
    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private EntityManager entityManager;

    private Long userId;

    @BeforeEach
    void createUser() {
        userId = randomUserId();
    }

    @Test
    void findScheduledOnReturnsDailyHabitsAndHabitsMatchingWeekday() {
        Habit daily = save(userId, "daily", DATE.minusDays(7), HabitFrequency.DAILY, Set.of());
        Habit matching = save(userId, "matching", DATE.minusDays(7), HabitFrequency.SPECIFIC_WEEKDAYS,
                Set.of(DATE.getDayOfWeek()));
        save(userId, "other-day", DATE.minusDays(7), HabitFrequency.SPECIFIC_WEEKDAYS,
                Set.of(DATE.getDayOfWeek().plus(1)));
        save(userId, "future-start", DATE.plusDays(1), HabitFrequency.DAILY, Set.of());
        save(randomUserId(), "other-user", DATE.minusDays(7), HabitFrequency.DAILY, Set.of());

        List<Habit> found = habitRepository.findScheduledOn(userId, DATE, DATE.getDayOfWeek(),
                HabitFrequency.DAILY);

        assertThat(found).extracting(Habit::getId).containsExactlyInAnyOrder(daily.getId(), matching.getId());
    }

    @Test
    void findEarliestStartDateReturnsMinimumForUser() {
        save(userId, "later", DATE, HabitFrequency.DAILY, Set.of());
        save(userId, "earlier", DATE.minusDays(5), HabitFrequency.DAILY, Set.of());
        save(randomUserId(), "other", DATE.minusDays(30), HabitFrequency.DAILY, Set.of());

        assertThat(habitRepository.findEarliestStartDate(userId)).contains(DATE.minusDays(5));
        assertThat(habitRepository.findEarliestStartDate(randomUserId())).isEmpty();
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        Habit habit = save(userId, "mine", DATE, HabitFrequency.DAILY, Set.of());

        assertThat(habitRepository.findByIdAndUserId(habit.getId(), userId)).isPresent();
        assertThat(habitRepository.findByIdAndUserId(habit.getId(), randomUserId())).isEmpty();
    }

    @Test
    void findStartedByReturnsHabitsStartedNotLaterThanBoundary() {
        Habit started = save(userId, "started", DATE, HabitFrequency.DAILY, Set.of());
        save(userId, "future", DATE.plusDays(1), HabitFrequency.DAILY, Set.of());

        assertThat(habitRepository.findStartedBy(userId, DATE)).extracting(Habit::getId)
                .containsExactly(started.getId());
    }

    @Test
    void persistsIsoWeekdayNumbers() {
        Habit habit = save(userId, "weekdays", DATE, HabitFrequency.SPECIFIC_WEEKDAYS,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY));
        entityManager.flush();
        entityManager.clear();

        Habit reloaded = habitRepository.findById(habit.getId()).orElseThrow();

        assertThat(reloaded.getWeekdays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
    }

    private Habit save(Long ownerId, String name, LocalDate startDate, HabitFrequency frequency,
            Set<DayOfWeek> weekdays) {
        return habitRepository.saveAndFlush(new Habit(ownerId, name, startDate, frequency, weekdays, NOW));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }
}
