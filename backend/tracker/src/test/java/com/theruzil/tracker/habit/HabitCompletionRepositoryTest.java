package com.theruzil.tracker.habit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class HabitCompletionRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 13);
    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private HabitCompletionRepository completionRepository;

    private Long habitId;

    @BeforeEach
    void createHabit() {
        habitId = habitRepository
                .saveAndFlush(new Habit(randomUserId(), ActivityType.HABIT, "habit", DATE, HabitFrequency.DAILY,
                        Set.of(), NOW))
                .getId();
    }

    @Test
    void rejectsDuplicateCompletionOnSameDate() {
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, NOW));

        assertThatThrownBy(() -> completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void detectsAndDeletesCompletion() {
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, NOW));

        assertThat(completionRepository.existsByHabitIdAndCompletedOn(habitId, DATE)).isTrue();
        assertThat(completionRepository.findByHabitIdAndCompletedOn(habitId, DATE)).isPresent();

        completionRepository.deleteByHabitIdAndCompletedOn(habitId, DATE);

        assertThat(completionRepository.existsByHabitIdAndCompletedOn(habitId, DATE)).isFalse();
    }

    @Test
    void storesTimeOfDayAndTreatsMissingValueAsAny() {
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, TimeOfDay.MORNING, NOW));
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE.minusDays(1), now()));

        List<HabitCompletion> found = completionRepository
                .findByHabitIdInAndCompletedOnBetween(List.of(habitId), DATE.minusDays(1), DATE);

        assertThat(found).extracting(HabitCompletion::getTimeOfDay)
                .containsExactlyInAnyOrder(TimeOfDay.MORNING, null);
        assertThat(found).extracting(HabitCompletion::getTimeOfDayValue)
                .containsExactlyInAnyOrder("MORNING", "ANY");
    }

    @Test
    void allowsSameDateForDifferentTimeOfDay() {
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, now()));
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, TimeOfDay.MORNING, NOW));
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, TimeOfDay.EVENING, NOW));

        assertThat(completionRepository.existsByHabitIdAndCompletedOn(habitId, DATE)).isTrue();
        assertThat(completionRepository.existsByHabitIdAndCompletedOnAndTimeOfDay(habitId, DATE, null)).isTrue();
        assertThat(completionRepository.existsByHabitIdAndCompletedOnAndTimeOfDay(habitId, DATE,
                TimeOfDay.MORNING)).isTrue();
        assertThat(completionRepository.existsByHabitIdAndCompletedOnAndTimeOfDay(habitId, DATE,
                TimeOfDay.EVENING)).isTrue();
    }

    @Test
    void rejectsDuplicateCompletionForSameTimeOfDay() {
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, TimeOfDay.MORNING, NOW));

        assertThatThrownBy(() -> completionRepository
                .saveAndFlush(new HabitCompletion(habitId, DATE, TimeOfDay.MORNING, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletesOnlyRequestedTimeOfDay() {
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, TimeOfDay.MORNING, NOW));
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, TimeOfDay.EVENING, NOW));

        completionRepository.deleteByHabitIdAndCompletedOnAndTimeOfDay(habitId, DATE, TimeOfDay.MORNING);

        assertThat(completionRepository.existsByHabitIdAndCompletedOnAndTimeOfDay(habitId, DATE,
                TimeOfDay.MORNING)).isFalse();
        assertThat(completionRepository.existsByHabitIdAndCompletedOnAndTimeOfDay(habitId, DATE,
                TimeOfDay.EVENING)).isTrue();
    }

    @Test
    void deletingMissingCompletionDoesNotFail() {
        completionRepository.deleteByHabitIdAndCompletedOn(habitId, DATE.minusDays(1));

        assertThat(completionRepository.existsByHabitIdAndCompletedOn(habitId, DATE.minusDays(1))).isFalse();
    }

    @Test
    void findsCompletionsWithinRangeForGivenHabits() {
        Long otherHabitId = habitRepository
                .saveAndFlush(new Habit(randomUserId(), ActivityType.HABIT, "other", DATE, HabitFrequency.DAILY,
                        Set.of(), NOW))
                .getId();
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, NOW));
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE.minusDays(10), NOW));
        completionRepository.saveAndFlush(new HabitCompletion(otherHabitId, DATE, NOW));

        List<HabitCompletion> found = completionRepository.findByHabitIdInAndCompletedOnBetween(List.of(habitId),
                DATE.minusDays(1), DATE);

        assertThat(found).extracting(HabitCompletion::getCompletedOn).containsExactly(DATE);
    }

    @Test
    void deletingHabitCascadesCompletionRemoval() {
        completionRepository.saveAndFlush(new HabitCompletion(habitId, DATE, NOW));
        Habit habit = habitRepository.findById(habitId).orElseThrow();

        habitRepository.delete(habit);
        habitRepository.flush();

        assertThat(completionRepository.existsByHabitIdAndCompletedOn(habitId, DATE)).isFalse();
    }

    private static Instant now() {
        return NOW;
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }
}
