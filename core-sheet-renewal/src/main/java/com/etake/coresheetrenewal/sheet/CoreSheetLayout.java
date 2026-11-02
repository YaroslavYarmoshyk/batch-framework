package com.etake.coresheetrenewal.sheet;

import com.etake.coresheetrenewal.sheet.CoreSheetRow.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The rows of a month sheet: each region row is followed by its stores, and the company row ends the data. The sheet
 * is the source of truth for which store belongs to which region, because managers regroup the stores between months.
 *
 * @param rows data rows in sheet order, the company row last
 */
public record CoreSheetLayout(List<CoreSheetRow> rows) {
    public static final int FIRST_DATA_ROW = 3;
    private static final String COMPANY_ROW_MARKER = "Разом";

    /**
     * @param names         column A of the data rows, as formulas
     * @param firstDayCells the first day column of the data rows, as formulas: only summary rows hold a formula there
     */
    public static CoreSheetLayout parse(final List<String> names, final List<String> firstDayCells) {
        final List<CoreSheetRow> rows = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i).trim();
            if (name.isEmpty()) {
                continue;
            }
            final int number = FIRST_DATA_ROW + i;
            if (name.contains(COMPANY_ROW_MARKER)) {
                rows.add(new CoreSheetRow(number, Kind.COMPANY, name));
                return new CoreSheetLayout(rows);
            }
            final boolean summary = i < firstDayCells.size() && firstDayCells.get(i).startsWith("=");
            rows.add(new CoreSheetRow(number, summary ? Kind.REGION : Kind.STORE, name));
        }
        throw new IllegalStateException("Company row («%s…») not found in the sheet".formatted(COMPANY_ROW_MARKER));
    }

    public List<CoreSheetRow> stores() {
        return rows.stream().filter(row -> row.kind() == Kind.STORE).toList();
    }

    /** The stores a summary row adds up: those below a region row up to the next summary, or all for the company. */
    public List<CoreSheetRow> storesOf(final CoreSheetRow summary) {
        if (summary.kind() == Kind.COMPANY) {
            return stores();
        }
        final int start = rows.indexOf(summary) + 1;
        final int end = IntStream.range(start, rows.size())
                .filter(i -> rows.get(i).kind() != Kind.STORE)
                .findFirst()
                .orElse(rows.size());
        return rows.subList(start, end);
    }

    public int lastRowNumber() {
        return rows.getLast().number();
    }
}
