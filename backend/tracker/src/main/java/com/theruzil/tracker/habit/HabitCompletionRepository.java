package com.theruzil.tracker.habit;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {

    boolean existsByHabitIdAndCompletedOn(Long habitId, LocalDate completedOn);

    Optional<HabitCompletion> findByHabitIdAndCompletedOn(Long habitId, LocalDate completedOn);

    void deleteByHabitIdAndCompletedOn(Long habitId, LocalDate completedOn);

    boolean existsByHabitIdAndCompletedOnAndTimeOfDayValue(Long habitId, LocalDate completedOn, String timeOfDayValue);

    void deleteByHabitIdAndCompletedOnAndTimeOfDayValue(Long habitId, LocalDate completedOn, String timeOfDayValue);

    default boolean existsByHabitIdAndCompletedOnAndTimeOfDay(Long habitId, LocalDate completedOn,
            TimeOfDay timeOfDay) {
        return existsByHabitIdAndCompletedOnAndTimeOfDayValue(habitId, completedOn,
                HabitCompletion.storageValue(timeOfDay));
    }

    default void deleteByHabitIdAndCompletedOnAndTimeOfDay(Long habitId, LocalDate completedOn,
            TimeOfDay timeOfDay) {
        deleteByHabitIdAndCompletedOnAndTimeOfDayValue(habitId, completedOn,
                HabitCompletion.storageValue(timeOfDay));
    }

    List<HabitCompletion> findByHabitIdInAndCompletedOnBetween(Collection<Long> habitIds, LocalDate from, LocalDate to);
}
