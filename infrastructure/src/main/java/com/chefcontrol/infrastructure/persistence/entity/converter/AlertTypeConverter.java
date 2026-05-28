package com.chefcontrol.infrastructure.persistence.entity.converter;

import com.chefcontrol.domain.alert.AlertType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AlertTypeConverter implements AttributeConverter<AlertType, String> {

    @Override
    public String convertToDatabaseColumn(AlertType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public AlertType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AlertType.valueOf(dbData.toUpperCase());
    }
}
