package com.theruzil.tracker.habit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.tracker.habit.dto.HabitHistoryEntry;
import com.theruzil.tracker.habit.dto.HabitHistoryResponse;
import com.theruzil.tracker.habit.dto.HabitOnDateResponse;
import com.theruzil.tracker.habit.dto.HabitRequest;
import com.theruzil.tracker.habit.dto.HabitResponse;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    private static final Long USER_ID = 10L;
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 13);
    private static final ActivityType HABIT = ActivityType.HABIT;
    private static final ActivityType TASK = ActivityType.TASK;

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitCompletionRepository completionRepository;

    private HabitService habitService;

    @BeforeEach
    void setUp() {
        habitService = new HabitService(habitRepository, completionRepository, Clock.fixed(NOW, ZONE));
    }

    @Test
    void fixedInstantFallsOnExpectedDateInConfiguredZone() {
        assertThat(LocalDate.ofInstant(NOW, ZONE)).isEqualTo(TODAY);
        assertThat(LocalDate.ofInstant(NOW, ZoneId.of("UTC"))).isEqualTo(LocalDate.of(2026, 5, 13));
    }

    @Test
    void createsDailyHabitWithTodayStartDate() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.create(USER_ID,
                new HabitRequest("  Morning run  ", TODAY, HabitFrequency.DAILY, null, HABIT));

        assertThat(response.name()).isEqualTo("Morning run");
        assertThat(response.startDate()).isEqualTo(TODAY);
        assertThat(response.frequency()).isEqualTo(HabitFrequency.DAILY);
        assertThat(response.weekdays()).isEmpty();
        assertThat(response.type()).isEqualTo(HABIT);
        verify(habitRepository).save(any(Habit.class));
    }

    @Test
    void createsTaskActivity() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.create(USER_ID,
                new HabitRequest("Read", TODAY.minusDays(2), HabitFrequency.DAILY, null, TASK));

        assertThat(response.type()).isEqualTo(TASK);
        assertThat(response.name()).isEqualTo("Read");
        verify(habitRepository).save(any(Habit.class));
    }

    @Test
    void rejectsActivityWithoutType() {
        assertThatThrownBy(() -> habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY, HabitFrequency.DAILY, null, null)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void rejectsHabitWithFutureStartDate() {
        assertThatThrownBy(() -> habitService.create(USER_ID,
                new HabitRequest("Future", TODAY.plusDays(3), HabitFrequency.DAILY, null, HABIT)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void createsHabitWithSpecificWeekdaysInIsoOrder() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.create(USER_ID, new HabitRequest("Yoga", TODAY,
                HabitFrequency.SPECIFIC_WEEKDAYS, List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY), HABIT));

        assertThat(response.frequency()).isEqualTo(HabitFrequency.SPECIFIC_WEEKDAYS);
        assertThat(response.weekdays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void rejectsHabitWithoutName() {
        assertThatThrownBy(() -> habitService.create(USER_ID,
                new HabitRequest("   ", TODAY, HabitFrequency.DAILY, null, HABIT)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void createsHabitWithPastStartDate() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY.minusDays(7), HabitFrequency.DAILY, null, HABIT));

        assertThat(response.startDate()).isEqualTo(TODAY.minusDays(7));
        verify(habitRepository).save(any(Habit.class));
    }

    @Test
    void createsPastCompletionsForDailyActivityWhenRequested() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> {
            Habit saved = invocation.getArgument(0);
            setId(saved, 1L);
            return saved;
        });

        habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY.minusDays(7), HabitFrequency.DAILY, null, HABIT, true));

        assertThat(capturedCompletionDates()).containsExactly(TODAY.minusDays(7), TODAY.minusDays(6),
                TODAY.minusDays(5), TODAY.minusDays(4), TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1));
    }

    @Test
    void createsPastCompletionsOnlyOnScheduledWeekdays() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> {
            Habit saved = invocation.getArgument(0);
            setId(saved, 1L);
            return saved;
        });

        habitService.create(USER_ID, new HabitRequest("Habit", TODAY.minusDays(14),
                HabitFrequency.SPECIFIC_WEEKDAYS, List.of(TODAY.getDayOfWeek()), HABIT, true));

        assertThat(capturedCompletionDates()).containsExactly(TODAY.minusDays(14), TODAY.minusDays(7));
    }

    @Test
    void doesNotCreatePastCompletionsWhenStartDateIsToday() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        habitService.create(USER_ID, new HabitRequest("Habit", TODAY, HabitFrequency.DAILY, null, HABIT, true));

        verifyNoInteractions(completionRepository);
    }

    @Test
    void doesNotCreatePastCompletionsWhenFlagIsAbsent() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY.minusDays(7), HabitFrequency.DAILY, null, HABIT));

        verifyNoInteractions(completionRepository);
    }

    @Test
    void doesNotCreatePastCompletionsWhenFlagIsFalse() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY.minusDays(7), HabitFrequency.DAILY, null, HABIT, false));

        verifyNoInteractions(completionRepository);
    }

    @Test
    void createsPastCompletionsForTaskWhenRequested() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> {
            Habit saved = invocation.getArgument(0);
            setId(saved, 2L);
            return saved;
        });

        HabitResponse response = habitService.create(USER_ID,
                new HabitRequest("Task", TODAY.minusDays(2), HabitFrequency.DAILY, null, TASK, true));

        assertThat(response.type()).isEqualTo(TASK);
        assertThat(capturedCompletionDates()).containsExactly(TODAY.minusDays(2), TODAY.minusDays(1));
    }

    @Test
    void rejectedCreationDoesNotCreateCompletions() {
        assertThatThrownBy(() -> habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY.plusDays(3), HabitFrequency.DAILY, null, HABIT, true)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
        verifyNoInteractions(completionRepository);
    }

    @Test
    void rejectsHabitWithoutPeriodicity() {
        assertThatThrownBy(() -> habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY, HabitFrequency.SPECIFIC_WEEKDAYS, List.of(), HABIT)))
                .isInstanceOf(InvalidHabitDataException.class);
        assertThatThrownBy(() -> habitService.create(USER_ID, new HabitRequest("Habit", TODAY, null, null, HABIT)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void updatesHabitWithoutTouchingCompletions() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "Old", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.update(USER_ID, 1L, new HabitRequest("New", TODAY.minusDays(1),
                HabitFrequency.SPECIFIC_WEEKDAYS, List.of(DayOfWeek.TUESDAY), HABIT));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.startDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(response.weekdays()).containsExactly(DayOfWeek.TUESDAY);
        verifyNoInteractions(completionRepository);
    }

    @Test
    void updatePreservesExistingType() {
        Habit habit = habitWithId(1L, USER_ID, TASK, "Old", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.update(USER_ID, 1L,
                new HabitRequest("New", TODAY, HabitFrequency.DAILY, null, HABIT));

        assertThat(response.type()).isEqualTo(TASK);
    }

    @Test
    void rejectsUpdateWithFutureStartDate() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "Old", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.update(USER_ID, 1L,
                new HabitRequest("New", TODAY.plusDays(1), HabitFrequency.DAILY, null, HABIT)))
                .isInstanceOf(InvalidHabitDataException.class);

        verify(habitRepository, never()).save(any(Habit.class));
    }

    @Test
    void rejectsUpdateOfForeignHabit() {
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.update(USER_ID, 1L,
                new HabitRequest("New", TODAY, HabitFrequency.DAILY, null, HABIT)))
                .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void deletesOwnActivity() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "Old", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        habitService.delete(USER_ID, 1L);

        verify(habitRepository).delete(habit);
    }

    @Test
    void deletesOwnTask() {
        Habit task = habitWithId(2L, USER_ID, TASK, "Task", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(2L, USER_ID)).thenReturn(Optional.of(task));

        habitService.delete(USER_ID, 2L);

        verify(habitRepository).delete(task);
    }

    @Test
    void rejectsDeleteOfForeignHabit() {
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.delete(USER_ID, 1L))
                .isInstanceOf(HabitNotFoundException.class);

        verify(habitRepository, never()).delete(any(Habit.class));
    }

    @Test
    void rejectsDeleteOfMissingHabit() {
        when(habitRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.delete(USER_ID, 99L))
                .isInstanceOf(HabitNotFoundException.class);

        verify(habitRepository, never()).delete(any(Habit.class));
    }

    @Test
    void listsHabitsForTodayWhenDateMissing() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findScheduledOn(USER_ID, TODAY, TODAY.getDayOfWeek(), HabitFrequency.DAILY, null))
                .thenReturn(List.of(habit));

        List<HabitOnDateResponse> result = habitService.listOnDate(USER_ID, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(TODAY);
        assertThat(result.get(0).completed()).isFalse();
        assertThat(result.get(0).type()).isEqualTo(HABIT);
    }

    @Test
    void listsActivitiesOfRequestedTypeOnly() {
        Habit task = habitWithId(2L, USER_ID, TASK, "task", TODAY.minusDays(1), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findScheduledOn(USER_ID, TODAY, TODAY.getDayOfWeek(), HabitFrequency.DAILY, TASK))
                .thenReturn(List.of(task));

        List<HabitOnDateResponse> result = habitService.listOnDate(USER_ID, TODAY, TASK);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(TASK);
    }

    @Test
    void listsPastDateWithCompletionStatus() {
        LocalDate past = TODAY.minusDays(7);
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", past.minusDays(1), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findScheduledOn(USER_ID, past, past.getDayOfWeek(), HabitFrequency.DAILY, null))
                .thenReturn(List.of(habit));
        when(completionRepository.findByHabitIdInAndCompletedOnBetween(Set.of(1L), past, past))
                .thenReturn(List.of(new HabitCompletion(1L, past, NOW)));

        List<HabitOnDateResponse> result = habitService.listOnDate(USER_ID, past, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(past);
        assertThat(result.get(0).completed()).isTrue();
    }

    @Test
    void listsFutureDateWithoutError() {
        LocalDate future = TODAY.plusDays(10);
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findScheduledOn(USER_ID, future, future.getDayOfWeek(), HabitFrequency.DAILY, null))
                .thenReturn(List.of(habit));

        assertThat(habitService.listOnDate(USER_ID, future, null)).hasSize(1);
    }

    @Test
    void listsEmptyBeforeEarliestHabit() {
        LocalDate beforeEarliest = TODAY.minusDays(1);
        when(habitRepository.findScheduledOn(USER_ID, beforeEarliest, beforeEarliest.getDayOfWeek(),
                HabitFrequency.DAILY, null)).thenReturn(List.of());

        assertThat(habitService.listOnDate(USER_ID, beforeEarliest, null)).isEmpty();
    }

    @Test
    void listsEmptyWhenUserHasNoHabits() {
        when(habitRepository.findScheduledOn(USER_ID, TODAY, TODAY.getDayOfWeek(), HabitFrequency.DAILY, null))
                .thenReturn(List.of());

        assertThat(habitService.listOnDate(USER_ID, null, null)).isEmpty();
    }

    @Test
    void historyReturnsScheduledDaysWithCompletionStatus() {
        LocalDate from = TODAY.minusDays(2);
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", from, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findStartedBy(USER_ID, TODAY)).thenReturn(List.of(habit));
        when(completionRepository.findByHabitIdInAndCompletedOnBetween(Set.of(1L), from, TODAY))
                .thenReturn(List.of(new HabitCompletion(1L, from.plusDays(1), NOW)));

        List<HabitHistoryResponse> result = habitService.history(USER_ID, from, TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(HABIT);
        assertThat(result.get(0).days()).containsExactly(new HabitHistoryEntry(from, false),
                new HabitHistoryEntry(from.plusDays(1), true), new HabitHistoryEntry(TODAY, false));
    }

    @Test
    void historyIncludesBothTypes() {
        LocalDate from = TODAY.minusDays(1);
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", from, HabitFrequency.DAILY, Set.of());
        Habit task = habitWithId(2L, USER_ID, TASK, "task", from, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findStartedBy(USER_ID, TODAY)).thenReturn(List.of(habit, task));

        List<HabitHistoryResponse> result = habitService.history(USER_ID, from, TODAY);

        assertThat(result).extracting(HabitHistoryResponse::type).containsExactly(HABIT, TASK);
    }

    @Test
    void historyBelowEarliestStartsWithScheduledDays() {
        LocalDate from = TODAY.minusDays(10);
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY.minusDays(2), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findStartedBy(USER_ID, TODAY)).thenReturn(List.of(habit));
        when(completionRepository.findByHabitIdInAndCompletedOnBetween(Set.of(1L), from, TODAY))
                .thenReturn(List.of());

        List<HabitHistoryResponse> result = habitService.history(USER_ID, from, TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).days()).containsExactly(new HabitHistoryEntry(TODAY.minusDays(2), false),
                new HabitHistoryEntry(TODAY.minusDays(1), false), new HabitHistoryEntry(TODAY, false));
    }

    @Test
    void historyIncludesTodayAndAllowsFutureBoundary() {
        LocalDate to = TODAY.plusDays(1);
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findStartedBy(USER_ID, to)).thenReturn(List.of(habit));
        when(completionRepository.findByHabitIdInAndCompletedOnBetween(Set.of(1L), TODAY, to))
                .thenReturn(List.of());

        List<HabitHistoryResponse> result = habitService.history(USER_ID, TODAY, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).days()).containsExactly(new HabitHistoryEntry(TODAY, false),
                new HabitHistoryEntry(to, false));
    }

    @Test
    void historySkipsHabitsWithoutScheduledDaysInRange() {
        Habit habit = habitWithId(2L, USER_ID, HABIT, "weekly", TODAY.minusDays(30),
                HabitFrequency.SPECIFIC_WEEKDAYS, Set.of(TODAY.getDayOfWeek().plus(1)));
        when(habitRepository.findStartedBy(USER_ID, TODAY)).thenReturn(List.of(habit));

        assertThat(habitService.history(USER_ID, TODAY, TODAY)).isEmpty();
    }

    @Test
    void historyRejectsStartAfterEnd() {
        assertThatThrownBy(() -> habitService.history(USER_ID, TODAY, TODAY.minusDays(1)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void historyIsEmptyWhenUserHasNoHabits() {
        when(habitRepository.findStartedBy(USER_ID, TODAY)).thenReturn(List.of());

        assertThat(habitService.history(USER_ID, TODAY, TODAY)).isEmpty();

        verifyNoInteractions(completionRepository);
    }

    @Test
    void marksTodayCompletion() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        HabitOnDateResponse response = habitService.markCompleted(USER_ID, 1L, TODAY);

        assertThat(response.completed()).isTrue();
        assertThat(response.date()).isEqualTo(TODAY);
        verify(completionRepository).save(any(HabitCompletion.class));
    }

    @Test
    void marksPastCompletion() {
        LocalDate past = TODAY.minusDays(3);
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY.minusDays(10), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        habitService.markCompleted(USER_ID, 1L, past);

        verify(completionRepository).save(any(HabitCompletion.class));
    }

    @Test
    void repeatedMarkDoesNotCreateDuplicate() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(completionRepository.existsByHabitIdAndCompletedOn(1L, TODAY)).thenReturn(true);

        HabitOnDateResponse response = habitService.markCompleted(USER_ID, 1L, TODAY);

        assertThat(response.completed()).isTrue();
        verify(completionRepository, never()).save(any(HabitCompletion.class));
    }

    @Test
    void rejectsFutureCompletion() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.markCompleted(USER_ID, 1L, TODAY.plusDays(1)))
                .isInstanceOf(InvalidHabitDataException.class);

        verify(completionRepository, never()).save(any(HabitCompletion.class));
    }

    @Test
    void rejectsCompletionBeforeStartDate() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY.minusDays(1), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.markCompleted(USER_ID, 1L, TODAY.minusDays(2)))
                .isInstanceOf(InvalidHabitDataException.class);
    }

    @Test
    void rejectsCompletionOnUnscheduledWeekday() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "weekly", TODAY.minusDays(10),
                HabitFrequency.SPECIFIC_WEEKDAYS, Set.of(TODAY.getDayOfWeek().plus(1)));
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.markCompleted(USER_ID, 1L, TODAY))
                .isInstanceOf(InvalidHabitDataException.class);
    }

    @Test
    void unmarksCompletion() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        HabitOnDateResponse response = habitService.unmarkCompleted(USER_ID, 1L, TODAY);

        assertThat(response.completed()).isFalse();
        verify(completionRepository).deleteByHabitIdAndCompletedOn(1L, TODAY);
    }

    @Test
    void unmarkingUnmarkedDateIsNoop() {
        Habit habit = habitWithId(1L, USER_ID, HABIT, "daily", TODAY.minusDays(5), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        HabitOnDateResponse response = habitService.unmarkCompleted(USER_ID, 1L, TODAY.minusDays(1));

        assertThat(response.completed()).isFalse();
        verify(completionRepository).deleteByHabitIdAndCompletedOn(1L, TODAY.minusDays(1));
    }

    @Test
    void rejectsCompletionOfForeignHabit() {
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.markCompleted(USER_ID, 1L, TODAY))
                .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void todayIsComputedInConfiguredZone() {
        Instant lateEveningUtc = Instant.parse("2026-05-13T23:30:00Z");
        LocalDate nextDayInMoscow = LocalDate.of(2026, 5, 14);
        HabitService moscowService = new HabitService(habitRepository, completionRepository,
                Clock.fixed(lateEveningUtc, ZONE));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse created = moscowService.create(USER_ID,
                new HabitRequest("Habit", nextDayInMoscow, HabitFrequency.DAILY, null, HABIT));

        assertThat(created.startDate()).isEqualTo(nextDayInMoscow);
        assertThatThrownBy(() -> moscowService.create(USER_ID,
                new HabitRequest("Habit", nextDayInMoscow.plusDays(1), HabitFrequency.DAILY, null, HABIT)))
                .isInstanceOf(InvalidHabitDataException.class);
    }

    @SuppressWarnings("unchecked")
    private List<LocalDate> capturedCompletionDates() {
        ArgumentCaptor<Iterable<HabitCompletion>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(completionRepository).saveAll(captor.capture());
        return StreamSupport.stream(captor.getValue().spliterator(), false)
                .map(HabitCompletion::getCompletedOn)
                .toList();
    }

    private static Habit habitWithId(Long id, Long userId, ActivityType type, String name, LocalDate startDate,
            HabitFrequency frequency, Set<DayOfWeek> weekdays) {
        Habit habit = new Habit(userId, type, name, startDate, frequency, weekdays, NOW);
        setId(habit, id);
        return habit;
    }

    private static void setId(Habit habit, Long id) {
        try {
            Field field = Habit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(habit, id);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
