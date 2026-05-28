package com.chefcontrol.infrastructure.time;

import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.shared.time.TimeProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class SystemTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return ChefControlTime.nowInstant();
    }

    @Override
    public LocalDateTime nowDateTime() {
        return ChefControlTime.nowDateTime();
    }

    @Override
    public LocalDate nowDate() {
        return ChefControlTime.nowDate();
    }
}
