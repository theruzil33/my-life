package com.theruzil.tracker.habit;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import com.theruzil.tracker.habit.dto.ImportedCompletion;
import com.theruzil.tracker.habit.dto.TimeOfDayCompletion;

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
                requestedWeekdays(request), requestedTimeOfDay(request), now);
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
                requestedTimeOfDay(request), Instant.now(clock));
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
        Map<Long, Set<TimeOfDay>> completedSlots = new HashMap<>();
        Set<Long> sharedCompleted = new HashSet<>();
        if (!habits.isEmpty()) {
            Set<Long> habitIds = habits.stream().map(Habit::getId).collect(Collectors.toSet());
            for (HabitCompletion completion : completionRepository.findByHabitIdInAndCompletedOnBetween(habitIds, target,
                    target)) {
                if (completion.getTimeOfDay() == null) {
                    sharedCompleted.add(completion.getHabitId());
                }
                else {
                    completedSlots.computeIfAbsent(completion.getHabitId(), key -> new HashSet<>())
                            .add(completion.getTimeOfDay());
                }
            }
        }
        return habits.stream()
                .map(habit -> toOnDateResponse(habit, target,
                        completedSlots.getOrDefault(habit.getId(), Set.of()),
                        sharedCompleted.contains(habit.getId())))
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
        Map<Long, Set<CompletionKey>> completedByHabit = completedKeysByHabit(habits, from, to);
        List<HabitHistoryResponse> result = new ArrayList<>();
        for (Habit habit : habits) {
            Set<CompletionKey> completedKeys = completedByHabit.getOrDefault(habit.getId(), Set.of());
            List<TimeOfDay> timeOfDay = sortedTimeOfDay(habit);
            List<HabitHistoryEntry> days = new ArrayList<>();
            for (LocalDate day : scheduledDates(habit, from, to)) {
                if (timeOfDay.isEmpty()) {
                    days.add(new HabitHistoryEntry(day, completedKeys.contains(new CompletionKey(day, null)), null));
                }
                else {
                    for (TimeOfDay slot : timeOfDay) {
                        days.add(new HabitHistoryEntry(day, completedKeys.contains(new CompletionKey(day, slot)),
                                slot));
                    }
                }
            }
            if (!days.isEmpty()) {
                result.add(new HabitHistoryResponse(habit.getId(), habit.getName(), habit.getStartDate(),
                        habit.getFrequency(), sortedWeekdays(habit), days, habit.getType(), timeOfDay));
            }
        }
        return result;
    }

    @Transactional
    public HabitOnDateResponse markCompleted(Long userId, Long habitId, LocalDate date, TimeOfDay timeOfDay) {
        Habit habit = requireHabit(userId, habitId);
        validateCompletionDate(habit, date, timeOfDay);
        if (!completionRepository.existsByHabitIdAndCompletedOnAndTimeOfDay(habitId, date, timeOfDay)) {
            completionRepository.save(new HabitCompletion(habitId, date, timeOfDay, Instant.now(clock)));
        }
        return completionResponse(habit, date, timeOfDay, true);
    }

    @Transactional
    public HabitOnDateResponse unmarkCompleted(Long userId, Long habitId, LocalDate date, TimeOfDay timeOfDay) {
        Habit habit = requireHabit(userId, habitId);
        validateCompletionDate(habit, date, timeOfDay);
        completionRepository.deleteByHabitIdAndCompletedOnAndTimeOfDay(habitId, date, timeOfDay);
        return completionResponse(habit, date, timeOfDay, false);
    }

    @Transactional
    public ImportedActivityResult importActivity(Long userId, ActivityType type, String name, LocalDate startDate,
            Set<TimeOfDay> timeOfDay, Collection<ImportedCompletion> importedCompletions) {
        Habit habit = habitRepository.findByUserIdAndTypeAndNameOrderById(userId, type, name).stream()
                .findFirst()
                .orElse(null);
        boolean created = false;
        if (habit == null) {
            Instant now = Instant.now(clock);
            habit = habitRepository.save(new Habit(userId, type, name, startDate, HabitFrequency.DAILY, Set.of(),
                    timeOfDay, now));
            created = true;
        }
        int saved = saveImportedCompletions(habit, importedCompletions);
        return new ImportedActivityResult(created, saved);
    }

    private int saveImportedCompletions(Habit habit, Collection<ImportedCompletion> importedCompletions) {
        if (importedCompletions == null || importedCompletions.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        LocalDate today = today();
        Set<CompletionKey> seen = new HashSet<>();
        List<HabitCompletion> completions = new ArrayList<>();
        for (ImportedCompletion imported : importedCompletions) {
            LocalDate date = imported.date();
            TimeOfDay timeOfDay = imported.timeOfDay();
            if (date == null || date.isAfter(today)) {
                continue;
            }
            if (!habit.isScheduledOn(date)) {
                continue;
            }
            if (habit.getTimeOfDay().isEmpty()) {
                if (timeOfDay != null) {
                    continue;
                }
            }
            else if (timeOfDay == null || !habit.getTimeOfDay().contains(timeOfDay)) {
                continue;
            }
            CompletionKey key = new CompletionKey(date, timeOfDay);
            if (seen.contains(key)
                    || completionRepository.existsByHabitIdAndCompletedOnAndTimeOfDay(habit.getId(), date, timeOfDay)) {
                continue;
            }
            seen.add(key);
            completions.add(new HabitCompletion(habit.getId(), date, timeOfDay, now));
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
        if (request.timeOfDay() != null) {
            if (request.timeOfDay().stream().anyMatch(Objects::isNull)) {
                throw new InvalidHabitDataException("Time of day must not be null");
            }
            if (new HashSet<>(request.timeOfDay()).size() != request.timeOfDay().size()) {
                throw new InvalidHabitDataException("Time of day must not contain duplicates");
            }
        }
    }

    private void validateCompletionDate(Habit habit, LocalDate date, TimeOfDay timeOfDay) {
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
        if (habit.getTimeOfDay().isEmpty() && timeOfDay != null) {
            throw new InvalidHabitDataException("Time of day must not be specified for activity without time of day");
        }
        if (!habit.getTimeOfDay().isEmpty() && timeOfDay == null) {
            throw new InvalidHabitDataException("Time of day is required for activity with time of day");
        }
        if (timeOfDay != null && !habit.getTimeOfDay().contains(timeOfDay)) {
            throw new InvalidHabitDataException("Time of day is not part of the activity");
        }
    }

    private Habit requireHabit(Long userId, Long habitId) {
        return habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found"));
    }

    private Map<Long, Set<CompletionKey>> completedKeysByHabit(List<Habit> habits, LocalDate from, LocalDate to) {
        if (habits.isEmpty()) {
            return Map.of();
        }
        Set<Long> habitIds = habits.stream().map(Habit::getId).collect(Collectors.toSet());
        Map<Long, Set<CompletionKey>> result = new HashMap<>();
        for (HabitCompletion completion : completionRepository.findByHabitIdInAndCompletedOnBetween(habitIds, from,
                to)) {
            result.computeIfAbsent(completion.getHabitId(), key -> new HashSet<>())
                    .add(new CompletionKey(completion.getCompletedOn(), completion.getTimeOfDay()));
        }
        return result;
    }

    private HabitOnDateResponse completionResponse(Habit habit, LocalDate date, TimeOfDay changed,
            boolean completed) {
        Set<TimeOfDay> completedSlots = new HashSet<>();
        boolean sharedCompleted = false;
        for (HabitCompletion completion : completionRepository.findByHabitIdInAndCompletedOnBetween(
                Set.of(habit.getId()), date, date)) {
            if (completion.getTimeOfDay() == null) {
                sharedCompleted = true;
            }
            else {
                completedSlots.add(completion.getTimeOfDay());
            }
        }
        if (changed == null) {
            sharedCompleted = completed;
        }
        else if (completed) {
            completedSlots.add(changed);
        }
        else {
            completedSlots.remove(changed);
        }
        return toOnDateResponse(habit, date, completedSlots, sharedCompleted);
    }

    private void markPastCompletions(Habit habit, Instant now) {
        LocalDate lastPastDate = today().minusDays(1);
        List<LocalDate> pastScheduledDates = scheduledDates(habit, habit.getStartDate(), lastPastDate);
        if (pastScheduledDates.isEmpty()) {
            return;
        }
        List<TimeOfDay> timeOfDay = sortedTimeOfDay(habit);
        List<HabitCompletion> completions = new ArrayList<>();
        for (LocalDate date : pastScheduledDates) {
            if (timeOfDay.isEmpty()) {
                completions.add(new HabitCompletion(habit.getId(), date, now));
            }
            else {
                for (TimeOfDay slot : timeOfDay) {
                    completions.add(new HabitCompletion(habit.getId(), date, slot, now));
                }
            }
        }
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

    private Set<TimeOfDay> requestedTimeOfDay(HabitRequest request) {
        if (request.timeOfDay() == null) {
            return Set.of();
        }
        return request.timeOfDay().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private HabitResponse toResponse(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getStartDate(), habit.getFrequency(),
                sortedWeekdays(habit), habit.getType(), sortedTimeOfDay(habit));
    }

    private HabitOnDateResponse toOnDateResponse(Habit habit, LocalDate date, Set<TimeOfDay> completedSlots,
            boolean sharedCompleted) {
        List<TimeOfDay> timeOfDay = sortedTimeOfDay(habit);
        List<TimeOfDayCompletion> completions = timeOfDay.stream()
                .map(slot -> new TimeOfDayCompletion(slot, completedSlots.contains(slot)))
                .toList();
        boolean completed = timeOfDay.isEmpty()
                ? sharedCompleted
                : !completions.isEmpty() && completions.stream().allMatch(TimeOfDayCompletion::completed);
        return new HabitOnDateResponse(habit.getId(), habit.getName(), habit.getStartDate(), habit.getFrequency(),
                sortedWeekdays(habit), date, completed, habit.getType(), timeOfDay, completions);
    }

    private List<DayOfWeek> sortedWeekdays(Habit habit) {
        return habit.getWeekdays().stream().sorted().toList();
    }

    private List<TimeOfDay> sortedTimeOfDay(Habit habit) {
        return habit.getTimeOfDay().stream().sorted().toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private record CompletionKey(LocalDate date, TimeOfDay timeOfDay) {
    }
}
