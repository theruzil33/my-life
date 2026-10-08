package com.theruzil.tracker.habit;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    Optional<Habit> findByIdAndUserId(Long id, Long userId);

    @Query("select min(h.startDate) from Habit h where h.userId = :userId")
    Optional<LocalDate> findEarliestStartDate(@Param("userId") Long userId);

    @Query("""
            select h from Habit h
            where h.userId = :userId
              and h.startDate <= :date
              and (:type is null or h.type = :type)
              and (h.frequency = :daily or :dayOfWeek member of h.weekdays)
            order by h.id
            """)
    List<Habit> findScheduledOn(@Param("userId") Long userId, @Param("date") LocalDate date,
            @Param("dayOfWeek") DayOfWeek dayOfWeek, @Param("daily") HabitFrequency daily,
            @Param("type") ActivityType type);

    @Query("""
            select h from Habit h
            where h.userId = :userId
              and h.startDate <= :to
            order by h.id
            """)
    List<Habit> findStartedBy(@Param("userId") Long userId, @Param("to") LocalDate to);
}
