package com.etake.salesplan.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class ExcelClasspathReader {
    private final ResourceLoader resourceLoader;

    public <T> List<T> read(String classpathLocation, Function<Row, T> rowMapper) {
        Resource resource = resourceLoader.getResource("classpath:" + classpathLocation);
        try (InputStream in = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            int first = sheet.getFirstRowNum() + 1;
            int last = sheet.getLastRowNum();

            List<T> result = new ArrayList<>();

            for (int r = first; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                T mapped = rowMapper.apply(row);
                if (mapped != null) {
                    result.add(mapped);
                }
            }

            return List.copyOf(result);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Excel from classpath: " + classpathLocation, e);
        }
    }
}
