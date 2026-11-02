package com.etake.coresheetrenewal.sheet;

import com.etake.coresheetrenewal.sheet.CoreSheetRow.Kind;
import com.etake.googlesheets.SheetRequests;
import com.google.api.services.sheets.v4.model.Request;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.etake.coresheetrenewal.sheet.CoreSheetLayout.FIRST_DATA_ROW;

/**
 * Changes the rows of a month sheet, keeping a model of them in step: the indexes of each request depend on all the
 * requests before it. The requests are meant for one atomic batch, ending with {@link #finish()}'s formula rewrite.
 */
public final class SheetStructureEditor {
    private final int sheetId;
    private final SummaryRowTemplates templates;
    private final Map<String, Integer> regionOrders;
    private final List<Line> lines;
    private final List<Request> requests = new ArrayList<>();

    private record Line(Kind kind, String name) {
    }

    /** @param regionOrders the sort order of regions by name, which decides where a new region goes */
    public SheetStructureEditor(final int sheetId, final CoreSheetLayout layout, final SummaryRowTemplates templates,
                                final Map<String, Integer> regionOrders) {
        this.sheetId = sheetId;
        this.templates = templates;
        this.regionOrders = regionOrders;
        this.lines = new ArrayList<>(layout.rows().stream().map(row -> new Line(row.kind(), row.name())).toList());
    }

    public boolean hasChanges() {
        return !requests.isEmpty();
    }

    public void removeStore(final String store) {
        removeLine(indexOf(Kind.STORE, store));
    }

    public void moveStore(final String store, final String region) {
        ensureRegion(region);
        final int source = indexOf(Kind.STORE, store);
        final int destination = endOfRegion(region);
        requests.add(SheetRequests.moveRows(sheetId, rowIndex(source), 1, rowIndex(destination)));
        final Line line = lines.remove(source);
        lines.add(destination > source ? destination - 1 : destination, line);
    }

    public void addStore(final String store, final String region) {
        ensureRegion(region);
        final int index = endOfRegion(region);
        lines.add(index, new Line(Kind.STORE, store));
        final int template = firstIndexOf(Kind.STORE, index);
        insertFromTemplate(index, template, store, templates.store());
    }

    public void removeEmptyRegions() {
        for (int i = lines.size() - 2; i >= 0; i--) {
            if (lines.get(i).kind() == Kind.REGION && lines.get(i + 1).kind() != Kind.STORE) {
                removeLine(i);
            }
        }
    }

    /** Puts the regions, each with its stores, in their sort order; regions without one go last, keeping their order. */
    public void sortRegions() {
        final List<String> sorted = lines.stream()
                .filter(line -> line.kind() == Kind.REGION)
                .map(Line::name)
                .sorted(Comparator.comparingInt(region -> regionOrders.getOrDefault(region, Integer.MAX_VALUE)))
                .toList();
        int destination = 0;
        for (final String region : sorted) {
            final int start = indexOf(Kind.REGION, region);
            final int end = endOfRegion(region);
            if (start != destination) {
                requests.add(SheetRequests.moveRows(sheetId, rowIndex(start), end - start, rowIndex(destination)));
                final List<Line> block = new ArrayList<>(lines.subList(start, end));
                lines.subList(start, end).clear();
                lines.addAll(destination, block);
            }
            destination += end - start;
        }
    }

    public CoreSheetLayout layout() {
        return new CoreSheetLayout(IntStream.range(0, lines.size())
                .mapToObj(i -> new CoreSheetRow(i + FIRST_DATA_ROW, lines.get(i).kind(), lines.get(i).name()))
                .toList());
    }

    /** All the requests, the rewrite of the summary formulas for the final rows included. */
    public List<Request> finish() {
        final List<Request> all = new ArrayList<>(requests);
        all.addAll(SummaryFormulas.requests(sheetId, layout(), templates));
        return all;
    }

    private void ensureRegion(final String region) {
        if (lines.stream().anyMatch(line -> line.kind() == Kind.REGION && line.name().equals(region))) {
            return;
        }
        final int index = newRegionIndex(region);
        lines.add(index, new Line(Kind.REGION, region));
        insertFromTemplate(index, firstIndexOf(Kind.REGION, index), region, templates.region());
    }

    /** Before the first region sorted after it; existing regions stay where they are, one without a sort order goes last. */
    private int newRegionIndex(final String region) {
        final Integer order = regionOrders.get(region);
        final int companyIndex = lines.size() - 1;
        if (order == null) {
            return companyIndex;
        }
        return IntStream.range(0, companyIndex)
                .filter(i -> lines.get(i).kind() == Kind.REGION)
                .filter(i -> regionOrders.getOrDefault(lines.get(i).name(), Integer.MIN_VALUE) > order)
                .findFirst()
                .orElse(companyIndex);
    }

    /** Inserts a row styled like the template row, with its formulas shifted to the new row and no values. */
    private void insertFromTemplate(final int index, final int templateIndex, final String name, final List<String> template) {
        final int row = rowIndex(index);
        requests.add(SheetRequests.insertRow(sheetId, row));
        requests.add(SheetRequests.copyRow(sheetId, rowIndex(templateIndex), row));
        requests.add(SheetRequests.texts(sheetId, row, 0, List.of(name)));
        SummaryRowTemplates.valueColumns(template).forEach(column ->
                requests.add(SheetRequests.clearValues(SheetRequests.range(sheetId, row, 1, column, 1))));
    }

    private void removeLine(final int index) {
        requests.add(SheetRequests.deleteRow(sheetId, rowIndex(index)));
        lines.remove(index);
    }

    private int endOfRegion(final String region) {
        final int start = indexOf(Kind.REGION, region) + 1;
        return IntStream.range(start, lines.size())
                .filter(i -> lines.get(i).kind() != Kind.STORE)
                .findFirst()
                .orElseThrow();
    }

    private int indexOf(final Kind kind, final String name) {
        return lines.indexOf(new Line(kind, name));
    }

    /** The first line of the kind other than the one just inserted. */
    private int firstIndexOf(final Kind kind, final int except) {
        return IntStream.range(0, lines.size())
                .filter(i -> i != except && lines.get(i).kind() == kind)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The sheet has no %s row to copy".formatted(kind)));
    }

    private static int rowIndex(final int lineIndex) {
        return lineIndex + FIRST_DATA_ROW - 1;
    }
}
