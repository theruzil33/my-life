package com.theruzil.tracker.habit;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.tracker.habit.dto.HabitHistoryEntry;
import com.theruzil.tracker.habit.dto.HabitHistoryResponse;
import com.theruzil.tracker.habit.dto.HabitOnDateResponse;
import com.theruzil.tracker.habit.dto.HabitRequest;
import com.theruzil.tracker.habit.dto.HabitResponse;
import com.theruzil.tracker.habit.dto.ImportedActivityResult;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository completionRepository;
    private final Clock clock;

    public HabitService(HabitRepository habitRepository, HabitCompletionRepository completionRepository, Clock clock) {
        this.habitRepository = habitRepository;
        this.completionRepository = completionRepository;
        this.clock = clock;
    }

    @Transactional
    public HabitResponse create(Long userId, HabitRequest request) {
        validateDefinition(request);
        Instant now = Instant.now(clock);
        Habit habit = new Habit(userId, request.type(), request.name().trim(), request.startDate(), request.frequency(),
                requestedWeekdays(request), now);
        Habit saved = habitRepository.save(habit);
        if (Boolean.TRUE.equals(request.markPastCompleted())) {
            markPastCompletions(saved, now);
        }
        return toResponse(saved);
    }

    @Transactional
    public HabitResponse update(Long userId, Long habitId, HabitRequest request) {
        Habit habit = requireHabit(userId, habitId);
        validateDefinition(request);
        habit.update(request.name().trim(), request.startDate(), request.frequency(), requestedWeekdays(request),
                Instant.now(clock));
        return toResponse(habitRepository.save(habit));
    }

    @Transactional
    public void delete(Long userId, Long habitId) {
        Habit habit = requireHabit(userId, habitId);
        habitRepository.delete(habit);
    }

    @Transactional(readOnly = true)
    public List<HabitOnDateResponse> listOnDate(Long userId, LocalDate date, ActivityType type) {
        LocalDate target = date != null ? date : today();
        List<Habit> habits = habitRepository.findScheduledOn(userId, target, target.getDayOfWeek(),
                HabitFrequency.DAILY, type);
        Set<Long> completedHabitIds = completedHabitIds(habits, target, target);
        return habits.stream()
                .map(habit -> toOnDateResponse(habit, target, completedHabitIds.contains(habit.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HabitHistoryResponse> history(Long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidHabitDataException("History range is required");
        }
        if (from.isAfter(to)) {
            throw new InvalidHabitDataException("History range start must not be after range end");
        }
        List<Habit> habits = habitRepository.findStartedBy(userId, to);
        Map<Long, Set<LocalDate>> completedByHabit = completedDatesByHabit(habits, from, to);
        List<HabitHistoryResponse> result = new ArrayList<>();
        for (Habit habit : habits) {
            Set<LocalDate> completedDates = completedByHabit.getOrDefault(habit.getId(), Set.of());
            List<HabitHistoryEntry> days = scheduledDates(habit, from, to).stream()
                    .map(day -> new HabitHistoryEntry(day, completedDates.contains(day)))
                    .toList();
            if (!days.isEmpty()) {
                result.add(new HabitHistoryResponse(habit.getId(), habit.getName(), habit.getStartDate(),
                        habit.getFrequency(), sortedWeekdays(habit), days, habit.getType()));
            }
        }
        return result;
    }

    @Transactional
    public HabitOnDateResponse markCompleted(Long userId, Long habitId, LocalDate date) {
        Habit habit = requireHabit(userId, habitId);
        validateCompletionDate(habit, date);
        if (!completionRepository.existsByHabitIdAndCompletedOn(habitId, date)) {
            completionRepository.save(new HabitCompletion(habitId, date, Instant.now(clock)));
        }
        return toOnDateResponse(habit, date, true);
    }

    @Transactional
    public HabitOnDateResponse unmarkCompleted(Long userId, Long habitId, LocalDate date) {
        Habit habit = requireHabit(userId, habitId);
        validateCompletionDate(habit, date);
        completionRepository.deleteByHabitIdAndCompletedOn(habitId, date);
        return toOnDateResponse(habit, date, false);
    }

    @Transactional
    public ImportedActivityResult importActivity(Long userId, ActivityType type, String name, LocalDate startDate,
            Collection<LocalDate> completionDates) {
        Habit habit = habitRepository.findByUserIdAndTypeAndNameOrderById(userId, type, name).stream()
                .findFirst()
                .orElse(null);
        boolean created = false;
        if (habit == null) {
            Instant now = Instant.now(clock);
            habit = habitRepository.save(new Habit(userId, type, name, startDate, HabitFrequency.DAILY, Set.of(), now));
            created = true;
        }
        int saved = saveImportedCompletions(habit, completionDates);
        return new ImportedActivityResult(created, saved);
    }

    private int saveImportedCompletions(Habit habit, Collection<LocalDate> completionDates) {
        if (completionDates == null || completionDates.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        LocalDate today = today();
        List<HabitCompletion> completions = new ArrayList<>();
        for (LocalDate date : completionDates) {
            if (date == null || date.isAfter(today)) {
                continue;
            }
            if (!habit.isScheduledOn(date)) {
                continue;
            }
            if (completionRepository.existsByHabitIdAndCompletedOn(habit.getId(), date)) {
                continue;
            }
            completions.add(new HabitCompletion(habit.getId(), date, now));
        }
        if (completions.isEmpty()) {
            return 0;
        }
        completionRepository.saveAll(completions);
        return completions.size();
    }

    private void validateDefinition(HabitRequest request) {
        if (request == null) {
            throw new InvalidHabitDataException("Habit data is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidHabitDataException("Habit name must not be blank");
        }
        if (request.startDate() == null) {
            throw new InvalidHabitDataException("Habit start date is required");
        }
        if (request.startDate().isAfter(today())) {
            throw new InvalidHabitDataException("Habit start date must not be in the future");
        }
        if (request.frequency() == null) {
            throw new InvalidHabitDataException("Habit frequency is required");
        }
        if (request.type() == null) {
            throw new InvalidHabitDataException("Activity type is required");
        }
        if (request.frequency() == HabitFrequency.SPECIFIC_WEEKDAYS && requestedWeekdays(request).isEmpty()) {
            throw new InvalidHabitDataException("At least one weekday is required for specific weekdays frequency");
        }
    }

    private void validateCompletionDate(Habit habit, LocalDate date) {
        if (date == null) {
            throw new InvalidHabitDataException("Completion date is required");
        }
        if (date.isAfter(today())) {
            throw new InvalidHabitDataException("Completion date must not be in the future");
        }
        if (date.isBefore(habit.getStartDate())) {
            throw new InvalidHabitDataException("Completion date must not be before the habit start date");
        }
        if (!habit.isScheduledOn(date)) {
            throw new InvalidHabitDataException("Habit is not scheduled on the given date");
        }
    }

    private Habit requireHabit(Long userId, Long habitId) {
        return habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found"));
    }

    private Set<Long> completedHabitIds(List<Habit> habits, LocalDate from, LocalDate to) {
        if (habits.isEmpty()) {
            return Set.of();
        }
        Set<Long> habitIds = habits.stream().map(Habit::getId).collect(Collectors.toSet());
        return completionRepository.findByHabitIdInAndCompletedOnBetween(habitIds, from, to).stream()
                .map(HabitCompletion::getHabitId)
                .collect(Collectors.toSet());
    }

    private Map<Long, Set<LocalDate>> completedDatesByHabit(List<Habit> habits, LocalDate from, LocalDate to) {
        if (habits.isEmpty()) {
            return Map.of();
        }
        Set<Long> habitIds = habits.stream().map(Habit::getId).collect(Collectors.toSet());
        return completionRepository.findByHabitIdInAndCompletedOnBetween(habitIds, from, to).stream()
                .collect(Collectors.groupingBy(HabitCompletion::getHabitId,
                        Collectors.mapping(HabitCompletion::getCompletedOn, Collectors.toSet())));
    }

    private void markPastCompletions(Habit habit, Instant now) {
        LocalDate lastPastDate = today().minusDays(1);
        List<LocalDate> pastScheduledDates = scheduledDates(habit, habit.getStartDate(), lastPastDate);
        if (pastScheduledDates.isEmpty()) {
            return;
        }
        List<HabitCompletion> completions = pastScheduledDates.stream()
                .map(date -> new HabitCompletion(habit.getId(), date, now))
                .toList();
        completionRepository.saveAll(completions);
    }

    private List<LocalDate> scheduledDates(Habit habit, LocalDate from, LocalDate to) {
        LocalDate cursor = from.isBefore(habit.getStartDate()) ? habit.getStartDate() : from;
        List<LocalDate> dates = new ArrayList<>();
        while (!cursor.isAfter(to)) {
            if (habit.isScheduledOn(cursor)) {
                dates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    private Set<DayOfWeek> requestedWeekdays(HabitRequest request) {
        if (request.frequency() != HabitFrequency.SPECIFIC_WEEKDAYS || request.weekdays() == null) {
            return Set.of();
        }
        return request.weekdays().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private HabitResponse toResponse(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getStartDate(), habit.getFrequency(),
                sortedWeekdays(habit), habit.getType());
    }

    private HabitOnDateResponse toOnDateResponse(Habit habit, LocalDate date, boolean completed) {
        return new HabitOnDateResponse(habit.getId(), habit.getName(), habit.getStartDate(), habit.getFrequency(),
                sortedWeekdays(habit), date, completed, habit.getType());
    }

    private List<DayOfWeek> sortedWeekdays(Habit habit) {
        return habit.getWeekdays().stream().sorted().toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
