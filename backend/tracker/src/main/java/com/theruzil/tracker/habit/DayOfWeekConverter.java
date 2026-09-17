package com.theruzil.tracker.habit;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek attribute) {
        return attribute == null ? null : (short) attribute.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : DayOfWeek.of(dbData);
    }
}
