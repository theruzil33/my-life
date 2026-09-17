package com.theruzil.tracker.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrackerConfiguration {

    @Bean
    public Clock trackerClock(@Value("${tracker.time-zone:Europe/Moscow}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
