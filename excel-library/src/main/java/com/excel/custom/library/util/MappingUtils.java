package com.excel.custom.library.util;

import com.excel.custom.library.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.excel.custom.library.util.Constants.DEFAULT_DATE_FORMAT;

@Slf4j
public final class MappingUtils {

    public static <T> T castToType(final Object obj, final Class<T> clazz) {
        try {
            final String removedInappropriateCharacters = obj.toString().strip()
                    .replaceAll(" ", "");
            return clazz.cast(switch (clazz.getSimpleName()) {
                case "Integer" -> Integer.valueOf(removedInappropriateCharacters.replaceAll(" ", ""));
                case "Double" -> Double.valueOf(removedInappropriateCharacters);
                case "String" -> removedInappropriateCharacters;
                case "Boolean" -> Boolean.valueOf(removedInappropriateCharacters);
                case "BigDecimal" -> getBigDecimalFromString(removedInappropriateCharacters.replaceAll(" ", "")
                        .replaceAll(",", ""));
                case "LocalDate" -> LocalDate.parse(removedInappropriateCharacters, DEFAULT_DATE_FORMAT);
                default -> throw new SystemException("Cannot cast object " + obj + " to " + clazz);
            });
        } catch (Exception e) {
            if (obj == null || !obj.toString().equals("#NULL!") && Strings.isNotBlank(obj.toString())) {
                log.debug("Cannot cast {} to type {}", obj, clazz);
            }
            return null;
        }
    }

    private static BigDecimal getBigDecimalFromString(final Object obj) {
        return Optional.ofNullable(obj)
                .map(Object::toString)
                .filter(Strings::isNotBlank)
                .map(str -> str.replace(',', '.'))
                .map(Double::parseDouble)
                .map(BigDecimal::valueOf)
                .orElse(null);
    }
}
