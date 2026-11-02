package com.etake.coresheetrenewal.sheet;

import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.service.StoreClosureRule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Brings the rows of month sheets in line with the database. The finished month's sheet records who traded in it, so
 * it only gains the stores missing from it; closures, moves between regions and the sorting of regions start with the
 * new month's sheet.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StoreStructureSync {

    /**
     * Adds the stores that had sales but have no row. A store whose region is unknown stops the job before any write.
     * A region is only created for a store if it has open stores: a region with none is not reported.
     *
     * @param monthStart the first day of the new month; a store closed before it is no longer open
     */
    public static void addMissingStores(final SheetStructureEditor editor,
                                        final CoreSheetLayout layout,
                                        final Collection<String> storesWithSales,
                                        final Map<String, StoreLocation> locations,
                                        final LocalDate monthStart) {
        final Set<String> onSheet = layout.stores().stream().map(CoreSheetRow::name).collect(Collectors.toSet());
        final List<String> missing = storesWithSales.stream().filter(store -> !onSheet.contains(store)).sorted().toList();

        final List<String> withoutRegion = missing.stream().filter(store -> regionOf(store, locations) == null).toList();
        if (!withoutRegion.isEmpty()) {
            throw new IllegalStateException("Stores with sales but without a row or a region in the database: " + withoutRegion);
        }
        final Set<String> reportedRegions = layout.rows().stream()
                .filter(row -> row.kind() == CoreSheetRow.Kind.REGION)
                .map(CoreSheetRow::name)
                .collect(Collectors.toSet());
        locations.values().stream()
                .filter(location -> location.regionName() != null && !location.closedBefore(monthStart))
                .map(StoreLocation::regionName)
                .forEach(reportedRegions::add);
        for (final String store : missing) {
            final String region = regionOf(store, locations);
            if (!reportedRegions.contains(region)) {
                log.warn("Skipping store «{}»: its region «{}» has no open stores, so its sales stay out of the totals", store, region);
                continue;
            }
            log.info("Adding store «{}» to region «{}»", store, region);
            editor.addStore(store, region);
        }
    }

    /**
     * Stops the job when a whole last day of the finished month has no sales in any store: its transactions are not
     * loaded yet, the actuals would be incomplete and every store would look closed.
     */
    public static void requireLastDaysLoaded(final LocalDate monthStart, final Map<String, StorePerformance> performance) {
        for (final LocalDate day : StoreClosureRule.lastDays(monthStart)) {
            if (performance.values().stream().noneMatch(store -> store.soldOn(day))) {
                throw new IllegalStateException("No store has sales on %s, are the transactions loaded?".formatted(day));
            }
        }
    }

    /**
     * Removes the stores that closed before the month, moves the stores whose region changed, drops regions left empty
     * and sorts the regions. See {@link StoreClosureRule} for what counts as closed.
     */
    public static void applyToNewMonth(final SheetStructureEditor editor,
                                       final CoreSheetLayout layout,
                                       final LocalDate monthStart,
                                       final Map<String, StoreLocation> locations,
                                       final Map<String, StorePerformance> performance,
                                       final Set<String> plannedStores) {
        for (final CoreSheetRow region : layout.rows().stream().filter(row -> row.kind() == CoreSheetRow.Kind.REGION).toList()) {
            for (final CoreSheetRow store : layout.storesOf(region)) {
                syncStore(editor, store.name(), region.name(), monthStart, locations.get(store.name()),
                        performance.get(store.name()), plannedStores.contains(store.name()));
            }
        }
        editor.removeEmptyRegions();
        editor.sortRegions();
    }

    private static void syncStore(final SheetStructureEditor editor, final String store, final String currentRegion,
                                  final LocalDate monthStart, final StoreLocation location, final StorePerformance sales,
                                  final boolean planned) {
        final boolean noRecentSales = StoreClosureRule.noSalesOnLastDays(sales == null ? Map.of() : sales.dailySales(), monthStart);
        if (noRecentSales && planned) {
            log.warn("Store «{}» has no sales on the last {} days of the month but has a plan for the new one, keeping it",
                    store, StoreClosureRule.DAYS_WITHOUT_SALES_TO_CLOSE);
        }
        if (location == null) {
            log.warn("Store «{}» is not in the database, its row is left as is", store);
        } else if (location.closedBefore(monthStart)) {
            log.info("Removing store «{}» closed on {}", store, location.closeDate());
            editor.removeStore(store);
        } else if (noRecentSales && !planned) {
            log.warn("Removing store «{}» without sales on the last {} days of the month, its close_date is {}",
                    store, StoreClosureRule.DAYS_WITHOUT_SALES_TO_CLOSE, location.closeDate());
            editor.removeStore(store);
        } else if (location.regionName() == null) {
            log.warn("Store «{}» has no region in the database, its row is left as is", store);
        } else if (!Objects.equals(location.regionName(), currentRegion)) {
            log.info("Moving store «{}» from region «{}» to «{}»", store, currentRegion, location.regionName());
            editor.moveStore(store, location.regionName());
        }
    }

    private static String regionOf(final String store, final Map<String, StoreLocation> locations) {
        final StoreLocation location = locations.get(store);
        return location == null ? null : location.regionName();
    }
}
