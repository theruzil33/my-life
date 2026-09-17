package com.theruzil.tracker.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.theruzil.tracker.habit.HabitService;
import com.theruzil.tracker.habit.dto.HabitHistoryResponse;
import com.theruzil.tracker.habit.dto.HabitOnDateResponse;
import com.theruzil.tracker.habit.dto.HabitRequest;
import com.theruzil.tracker.habit.dto.HabitResponse;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody HabitRequest request) {
        return habitService.create(userId, request);
    }

    @PutMapping("/{id}")
    public HabitResponse update(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @PathVariable Long id, @RequestBody HabitRequest request) {
        return habitService.update(userId, id, request);
    }

    @GetMapping
    public List<HabitOnDateResponse> listOnDate(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return habitService.listOnDate(userId, date);
    }

    @GetMapping("/history")
    public List<HabitHistoryResponse> history(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return habitService.history(userId, from, to);
    }

    @PutMapping("/{id}/completions/{date}")
    public HabitOnDateResponse markCompleted(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @PathVariable Long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return habitService.markCompleted(userId, id, date);
    }

    @DeleteMapping("/{id}/completions/{date}")
    public HabitOnDateResponse unmarkCompleted(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @PathVariable Long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return habitService.unmarkCompleted(userId, id, date);
    }
}
