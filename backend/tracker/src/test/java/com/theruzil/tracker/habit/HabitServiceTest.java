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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
                new HabitRequest("  Morning run  ", TODAY, HabitFrequency.DAILY, null));

        assertThat(response.name()).isEqualTo("Morning run");
        assertThat(response.startDate()).isEqualTo(TODAY);
        assertThat(response.frequency()).isEqualTo(HabitFrequency.DAILY);
        assertThat(response.weekdays()).isEmpty();
        verify(habitRepository).save(any(Habit.class));
    }

    @Test
    void createsHabitWithFutureStartDate() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.create(USER_ID,
                new HabitRequest("Future", TODAY.plusDays(3), HabitFrequency.DAILY, null));

        assertThat(response.startDate()).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    void createsHabitWithSpecificWeekdaysInIsoOrder() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.create(USER_ID, new HabitRequest("Yoga", TODAY,
                HabitFrequency.SPECIFIC_WEEKDAYS, List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)));

        assertThat(response.frequency()).isEqualTo(HabitFrequency.SPECIFIC_WEEKDAYS);
        assertThat(response.weekdays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void rejectsHabitWithoutName() {
        assertThatThrownBy(
                () -> habitService.create(USER_ID, new HabitRequest("   ", TODAY, HabitFrequency.DAILY, null)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void rejectsHabitWithPastStartDate() {
        assertThatThrownBy(() -> habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY.minusDays(1), HabitFrequency.DAILY, null)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void rejectsHabitWithoutPeriodicity() {
        assertThatThrownBy(() -> habitService.create(USER_ID,
                new HabitRequest("Habit", TODAY, HabitFrequency.SPECIFIC_WEEKDAYS, List.of())))
                .isInstanceOf(InvalidHabitDataException.class);
        assertThatThrownBy(() -> habitService.create(USER_ID, new HabitRequest("Habit", TODAY, null, null)))
                .isInstanceOf(InvalidHabitDataException.class);

        verifyNoInteractions(habitRepository);
    }

    @Test
    void updatesHabitWithoutTouchingCompletions() {
        Habit habit = habitWithId(1L, USER_ID, "Old", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse response = habitService.update(USER_ID, 1L, new HabitRequest("New", TODAY.plusDays(1),
                HabitFrequency.SPECIFIC_WEEKDAYS, List.of(DayOfWeek.TUESDAY)));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.startDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(response.weekdays()).containsExactly(DayOfWeek.TUESDAY);
        verifyNoInteractions(completionRepository);
    }

    @Test
    void rejectsUpdateWithPastStartDate() {
        Habit habit = habitWithId(1L, USER_ID, "Old", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.update(USER_ID, 1L,
                new HabitRequest("New", TODAY.minusDays(1), HabitFrequency.DAILY, null)))
                .isInstanceOf(InvalidHabitDataException.class);

        verify(habitRepository, never()).save(any(Habit.class));
    }

    @Test
    void rejectsUpdateOfForeignHabit() {
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.update(USER_ID, 1L,
                new HabitRequest("New", TODAY, HabitFrequency.DAILY, null)))
                .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void listsHabitsForTodayWhenDateMissing() {
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY.minusDays(3), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(TODAY.minusDays(3)));
        when(habitRepository.findScheduledOn(USER_ID, TODAY, TODAY.getDayOfWeek(), HabitFrequency.DAILY))
                .thenReturn(List.of(habit));

        List<HabitOnDateResponse> result = habitService.listOnDate(USER_ID, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(TODAY);
        assertThat(result.get(0).completed()).isFalse();
    }

    @Test
    void listsPastDateWithCompletionStatus() {
        LocalDate past = TODAY.minusDays(7);
        Habit habit = habitWithId(1L, USER_ID, "daily", past.minusDays(1), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(past.minusDays(1)));
        when(habitRepository.findScheduledOn(USER_ID, past, past.getDayOfWeek(), HabitFrequency.DAILY))
                .thenReturn(List.of(habit));
        when(completionRepository.findByHabitIdInAndCompletedOnBetween(Set.of(1L), past, past))
                .thenReturn(List.of(new HabitCompletion(1L, past, NOW)));

        List<HabitOnDateResponse> result = habitService.listOnDate(USER_ID, past);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(past);
        assertThat(result.get(0).completed()).isTrue();
    }

    @Test
    void listsFutureDateWithoutError() {
        LocalDate future = TODAY.plusDays(10);
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(TODAY));
        when(habitRepository.findScheduledOn(USER_ID, future, future.getDayOfWeek(), HabitFrequency.DAILY))
                .thenReturn(List.of(habit));

        assertThat(habitService.listOnDate(USER_ID, future)).hasSize(1);
    }

    @Test
    void rejectsListDateBeforeEarliestHabit() {
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(TODAY));

        assertThatThrownBy(() -> habitService.listOnDate(USER_ID, TODAY.minusDays(1)))
                .isInstanceOf(InvalidHabitDataException.class);

        verify(habitRepository, never()).findScheduledOn(any(), any(), any(), any());
    }

    @Test
    void listsEmptyWhenUserHasNoHabits() {
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.empty());
        when(habitRepository.findScheduledOn(USER_ID, TODAY, TODAY.getDayOfWeek(), HabitFrequency.DAILY))
                .thenReturn(List.of());

        assertThat(habitService.listOnDate(USER_ID, null)).isEmpty();
    }

    @Test
    void historyReturnsScheduledDaysWithCompletionStatus() {
        LocalDate from = TODAY.minusDays(2);
        Habit habit = habitWithId(1L, USER_ID, "daily", from, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(from));
        when(habitRepository.findStartedBy(USER_ID, TODAY)).thenReturn(List.of(habit));
        when(completionRepository.findByHabitIdInAndCompletedOnBetween(Set.of(1L), from, TODAY))
                .thenReturn(List.of(new HabitCompletion(1L, from.plusDays(1), NOW)));

        List<HabitHistoryResponse> result = habitService.history(USER_ID, from, TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).days()).containsExactly(new HabitHistoryEntry(from, false),
                new HabitHistoryEntry(from.plusDays(1), true), new HabitHistoryEntry(TODAY, false));
    }

    @Test
    void historyIncludesTodayAndAllowsFutureBoundary() {
        LocalDate to = TODAY.plusDays(1);
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(TODAY));
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
        Habit habit = habitWithId(2L, USER_ID, "weekly", TODAY.minusDays(30),
                HabitFrequency.SPECIFIC_WEEKDAYS, Set.of(TODAY.getDayOfWeek().plus(1)));
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(TODAY.minusDays(30)));
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
    void historyRejectsStartBeforeEarliestHabit() {
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.of(TODAY));

        assertThatThrownBy(() -> habitService.history(USER_ID, TODAY.minusDays(1), TODAY))
                .isInstanceOf(InvalidHabitDataException.class);
    }

    @Test
    void historyIsEmptyWhenUserHasNoHabits() {
        when(habitRepository.findEarliestStartDate(USER_ID)).thenReturn(Optional.empty());
        when(habitRepository.findStartedBy(USER_ID, TODAY)).thenReturn(List.of());

        assertThat(habitService.history(USER_ID, TODAY, TODAY)).isEmpty();

        verifyNoInteractions(completionRepository);
    }

    @Test
    void marksTodayCompletion() {
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        HabitOnDateResponse response = habitService.markCompleted(USER_ID, 1L, TODAY);

        assertThat(response.completed()).isTrue();
        assertThat(response.date()).isEqualTo(TODAY);
        verify(completionRepository).save(any(HabitCompletion.class));
    }

    @Test
    void marksPastCompletion() {
        LocalDate past = TODAY.minusDays(3);
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY.minusDays(10), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        habitService.markCompleted(USER_ID, 1L, past);

        verify(completionRepository).save(any(HabitCompletion.class));
    }

    @Test
    void repeatedMarkDoesNotCreateDuplicate() {
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(completionRepository.existsByHabitIdAndCompletedOn(1L, TODAY)).thenReturn(true);

        HabitOnDateResponse response = habitService.markCompleted(USER_ID, 1L, TODAY);

        assertThat(response.completed()).isTrue();
        verify(completionRepository, never()).save(any(HabitCompletion.class));
    }

    @Test
    void rejectsFutureCompletion() {
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.markCompleted(USER_ID, 1L, TODAY.plusDays(1)))
                .isInstanceOf(InvalidHabitDataException.class);

        verify(completionRepository, never()).save(any(HabitCompletion.class));
    }

    @Test
    void rejectsCompletionBeforeStartDate() {
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY.minusDays(1), HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.markCompleted(USER_ID, 1L, TODAY.minusDays(2)))
                .isInstanceOf(InvalidHabitDataException.class);
    }

    @Test
    void rejectsCompletionOnUnscheduledWeekday() {
        Habit habit = habitWithId(1L, USER_ID, "weekly", TODAY.minusDays(10),
                HabitFrequency.SPECIFIC_WEEKDAYS, Set.of(TODAY.getDayOfWeek().plus(1)));
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        assertThatThrownBy(() -> habitService.markCompleted(USER_ID, 1L, TODAY))
                .isInstanceOf(InvalidHabitDataException.class);
    }

    @Test
    void unmarksCompletion() {
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY, HabitFrequency.DAILY, Set.of());
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        HabitOnDateResponse response = habitService.unmarkCompleted(USER_ID, 1L, TODAY);

        assertThat(response.completed()).isFalse();
        verify(completionRepository).deleteByHabitIdAndCompletedOn(1L, TODAY);
    }

    @Test
    void unmarkingUnmarkedDateIsNoop() {
        Habit habit = habitWithId(1L, USER_ID, "daily", TODAY.minusDays(5), HabitFrequency.DAILY, Set.of());
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
                new HabitRequest("Habit", nextDayInMoscow, HabitFrequency.DAILY, null));

        assertThat(created.startDate()).isEqualTo(nextDayInMoscow);
        assertThatThrownBy(() -> moscowService.create(USER_ID,
                new HabitRequest("Habit", TODAY, HabitFrequency.DAILY, null)))
                .isInstanceOf(InvalidHabitDataException.class);
    }

    private static Habit habitWithId(Long id, Long userId, String name, LocalDate startDate,
            HabitFrequency frequency, Set<DayOfWeek> weekdays) {
        Habit habit = new Habit(userId, name, startDate, frequency, weekdays, NOW);
        try {
            Field field = Habit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(habit, id);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return habit;
    }
}
