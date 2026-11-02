package com.etake.coresheetrenewal.sheet;

import java.util.List;
import java.util.stream.IntStream;

/** The cells, as formulas and by column, of one store, one region and the company row of a month sheet. */
public record SummaryRowTemplates(List<String> store, List<String> region, List<String> company) {

    /** Columns to reset in a row just copied from a template: its values belong to the source row. */
    static List<Integer> valueColumns(final List<String> template) {
        return IntStream.range(1, template.size())
                .filter(column -> !template.get(column).startsWith("="))
                .boxed()
                .toList();
    }
}
