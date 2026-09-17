package com.theruzil.tracker.habit;

public class HabitNotFoundException extends RuntimeException {

    public HabitNotFoundException(String message) {
        super(message);
    }
}
