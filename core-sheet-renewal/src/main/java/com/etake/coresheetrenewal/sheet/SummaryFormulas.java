package com.etake.coresheetrenewal.sheet;

import com.etake.googlesheets.SheetRequests;
import com.google.api.services.sheets.v4.model.Request;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.etake.coresheetrenewal.sheet.CoreSheetLayout.FIRST_DATA_ROW;

/**
 * Re-points the formulas of the region and company rows at the current rows of the sheet. The formulas themselves stay
 * the sheet's own: they come from its template rows and only their references are rewritten, so a change of a formula
 * in the sheet carries over.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SummaryFormulas {
    /** {@code D3}, {@code M4:M14}, or a list of those in one column, such as {@code C3;C15} or {@code M4:M14;M16:M23}. */
    private static final Pattern REFERENCES = Pattern.compile(
            "(?<![A-Z$])(\\$?)([A-Z]{1,3})(\\$?)\\d+(:\\$?\\2\\$?\\d+)?(;\\$?\\2\\$?\\d+(:\\$?\\2\\$?\\d+)?)*");
    private static final Pattern REGION_NAMES_FILTER = Pattern.compile("(REGEXMATCH\\([^;]+;)\"[^\"]*\"\\)");

    public static List<Request> requests(final int sheetId, final CoreSheetLayout layout, final SummaryRowTemplates templates) {
        final List<Request> requests = new ArrayList<>();
        for (final CoreSheetRow row : layout.rows()) {
            switch (row.kind()) {
                case REGION -> addRow(requests, sheetId, row, templates.region(), layout);
                case COMPANY -> addRow(requests, sheetId, row, templates.company(), layout);
                case STORE -> { }
            }
        }
        return requests;
    }

    private static void addRow(final List<Request> requests, final int sheetId, final CoreSheetRow row,
                               final List<String> template, final CoreSheetLayout layout) {
        for (int column = 0; column < template.size(); column++) {
            if (template.get(column).startsWith("=")) {
                final String formula = rewrite(template.get(column), row, layout);
                requests.add(SheetRequests.fillFormula(SheetRequests.range(sheetId, row.number() - 1, 1, column, 1), formula));
            }
        }
    }

    static String rewrite(final String formula, final CoreSheetRow row, final CoreSheetLayout layout) {
        final String rewritten = REFERENCES.matcher(formula).replaceAll(match -> references(match, row, layout));
        if (row.kind() != CoreSheetRow.Kind.COMPANY) {
            return rewritten;
        }
        return REGION_NAMES_FILTER.matcher(rewritten).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + "\"" + regionNamesRegex(layout) + "\")"));
    }

    private static String references(final MatchResult match, final CoreSheetRow row, final CoreSheetLayout layout) {
        final String column = match.group(2);
        final boolean absolute = !match.group(1).isEmpty();
        final boolean range = match.group(4) != null;
        final boolean list = match.group(5) != null;
        final String references;
        if (!range && !list) {
            references = column + row.number();
        } else if (row.kind() == CoreSheetRow.Kind.REGION) {
            final List<CoreSheetRow> stores = layout.storesOf(row);
            references = columnRange(column, stores.getFirst().number(), stores.getLast().number(), false);
        } else if (!range) {
            references = layout.rows().stream()
                    .filter(candidate -> candidate.kind() == CoreSheetRow.Kind.REGION)
                    .map(region -> column + region.number())
                    .collect(Collectors.joining(";"));
        } else if (list) {
            references = layout.rows().stream()
                    .filter(candidate -> candidate.kind() == CoreSheetRow.Kind.REGION)
                    .map(layout::storesOf)
                    .map(stores -> columnRange(column, stores.getFirst().number(), stores.getLast().number(), false))
                    .collect(Collectors.joining(";"));
        } else {
            references = columnRange(column, FIRST_DATA_ROW, row.number() - 1, absolute);
        }
        return Matcher.quoteReplacement(references);
    }

    private static String columnRange(final String column, final int from, final int to, final boolean absolute) {
        final String prefix = absolute ? "$" + column + "$" : column;
        return prefix + from + ":" + prefix + to;
    }

    /** Anchored whole: a store called «Львів 1 Центр» must not count as the region «Львів 1». */
    private static String regionNamesRegex(final CoreSheetLayout layout) {
        return layout.rows().stream()
                .filter(row -> row.kind() == CoreSheetRow.Kind.REGION)
                .map(CoreSheetRow::name)
                .map(name -> name.replaceAll("([\\\\.^$|()\\[\\]{}*+?])", "\\\\$1"))
                .collect(Collectors.joining("|", "^(", ")$"));
    }
}
