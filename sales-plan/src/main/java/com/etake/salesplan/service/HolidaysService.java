package com.etake.salesplan.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class HolidaysService {
    private final ExcelClasspathReader excelClasspathReader;

    public Map<String, List<LocalDate>> getHolidays() {
        return excelClasspathReader.read("holidays/Holidays.xlsx", holidaysMapper())
                .stream()
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Function<Row, Map.Entry<String, List<LocalDate>>> holidaysMapper() {
        return row -> {
            Cell store = row.getCell(0);
            if (isNull(store)) {
                return null;
            }
            return Map.entry(
                    store.getStringCellValue(),
                    StreamSupport.stream(row.spliterator(), false)
                            .skip(1)
                            .map(Cell::getLocalDateTimeCellValue)
                            .filter(Objects::nonNull)
                            .map(LocalDateTime::toLocalDate)
                            .toList()
            );
        };
    }
}
