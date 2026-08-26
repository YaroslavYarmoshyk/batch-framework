package com.etake.turnoverplan.service;

import com.etake.turnoverplan.model.StoreCategorySales;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.Collection;

public interface ExcelService {

    Workbook getWorkbook(final Collection<StoreCategorySales> sales);

    void writeWorkbook(final Workbook workbook, final String path) throws Exception;
}
