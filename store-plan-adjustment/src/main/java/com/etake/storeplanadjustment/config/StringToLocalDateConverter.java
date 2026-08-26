package com.etake.storeplanadjustment.config;

import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@ConfigurationPropertiesBinding
public class StringToLocalDateConverter implements Converter<String, LocalDate> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    public LocalDate convert(@NonNull String source) {
        return LocalDate.parse(source, FORMATTER);
    }
}
