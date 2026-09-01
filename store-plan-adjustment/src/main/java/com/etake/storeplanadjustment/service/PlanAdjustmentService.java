package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.config.properties.SystemConfigurationProperties;
import com.etake.storeplanadjustment.model.AdjustedPlanReportRow;
import com.etake.storeplanadjustment.model.AdjustmentRequest;
import com.etake.storeplanadjustment.model.DailyPlan;
import com.etake.storeplanadjustment.model.NamedRef;
import com.etake.storeplanadjustment.model.ProfileKey;
import com.etake.storeplanadjustment.model.StoreCategoryDate;
import com.etake.storeplanadjustment.repository.CategoryRepository;
import com.etake.storeplanadjustment.repository.DailyPlanRepository;
import com.etake.storeplanadjustment.repository.LocationRepository;
import com.etake.storeplanadjustment.repository.TransactionSlotRepository;
import com.etake.storeplanadjustment.utils.SlotTurnovers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.etake.storeplanadjustment.utils.Constants.PRECISE_MATH_CONTEXT;

/**
 * Orchestrates the adjustment: resolve stores, pick the plans in range, build intraday profiles,
 * read the actual sales inside each disrupted window, and for disrupted (store, category, date)
 * combinations apply
 * <pre>adjusted = plan - (Σ share within range) * plan + (actual sales within range) - amount</pre>
 * where {@code amount} is a store/date's declared known-loss amount, allocated to this category in
 * proportion to plan turnover (see {@link #allocateAmount}); a row without a matching amount
 * contributes {@code 0}. The result is always capped at the plan - this job only ever reduces
 * plans, never increases them. Plans for dates that are not disrupted are reported unchanged.
 * Read-only against the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanAdjustmentService {
    private final SystemConfigurationProperties properties;
    private final AdjustmentFileService adjustmentFileService;
    private final LocationRepository locationRepository;
    private final CategoryRepository categoryRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final TransactionSlotRepository transactionSlotRepository;
    private final ShareProfileService shareProfileService;

    public List<AdjustedPlanReportRow> adjustPlans() {
        final LocalDate fromDate = properties.dateRange().fromDate();
        final LocalDate toDate = properties.dateRange().toDate();

        final List<AdjustmentRequest> adjustments = adjustmentFileService.readAdjustments();
        if (adjustments.isEmpty()) {
            log.warn("No adjustments to apply");
            return List.of();
        }

        // Only adjustment rows inside this run's date range decide which stores are relevant.
        final List<AdjustmentRequest> inRangeAdjustments = withinRange(adjustments, fromDate, toDate);
        if (inRangeAdjustments.isEmpty()) {
            log.warn("No adjustments fall within [{} .. {}]", fromDate, toDate);
            return List.of();
        }
        if (inRangeAdjustments.size() < adjustments.size()) {
            log.info("Ignoring {} adjustment(s) outside [{} .. {}]",
                    adjustments.size() - inRangeAdjustments.size(), fromDate, toDate);
        }

        final Map<String, String> storeNameToId = resolveStores(inRangeAdjustments);

        // storeId -> date -> disruption; and storeId -> all disrupted dates (for profile exclusion).
        final Map<String, Map<LocalDate, Disruption>> disruptionsByStore = indexDisruptions(inRangeAdjustments, storeNameToId);
        final Map<String, Set<LocalDate>> disruptedDatesByStore = disruptionsByStore.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().keySet()));

        if (disruptionsByStore.isEmpty()) {
            log.warn("No adjustments matched an open store");
            return List.of();
        }
        final Set<String> storeIds = disruptionsByStore.keySet();

        // Every plan in range for these stores is reported; only the disrupted subset feeds the formula.
        final List<DailyPlan> plans = dailyPlanRepository.findPlansToAdjust(fromDate, toDate, storeIds);
        if (plans.isEmpty()) {
            log.warn("No daily plans in [{} .. {}] for the stores with adjustments", fromDate, toDate);
            return List.of();
        }

        final List<DailyPlan> disruptedPlans = plans.stream()
                .filter(plan -> isDisrupted(disruptionsByStore, plan.storeId(), plan.date()))
                .toList();
        if (disruptedPlans.isEmpty()) {
            log.warn("No daily plans in [{} .. {}] match the disrupted store/date combinations - reporting all plans unadjusted", fromDate, toDate);
        }

        final Set<DayOfWeek> weekdays = disruptedPlans.stream().map(p -> p.date().getDayOfWeek()).collect(Collectors.toSet());
        final Map<ProfileKey, Map<LocalTime, BigDecimal>> profiles =
                shareProfileService.buildProfiles(fromDate, weekdays, storeIds, disruptedDatesByStore);

        final Set<LocalDate> planDates = disruptedPlans.stream().map(DailyPlan::date).collect(Collectors.toSet());
        final Map<StoreCategoryDate, Map<LocalTime, BigDecimal>> actualSales = SlotTurnovers.groupByStoreCategoryDate(
                transactionSlotRepository.findSlotTurnover(planDates, storeIds));

        // Needed to allocate a store/date's amount across its categories in proportion to plan turnover.
        final Map<StoreDateKey, BigDecimal> turnoverTotals = disruptedPlans.stream()
                .collect(Collectors.groupingBy(p -> new StoreDateKey(p.storeId(), p.date()),
                        Collectors.reducing(BigDecimal.ZERO, p -> nz(p.turnover()), BigDecimal::add)));
        final Map<StoreDateKey, Long> categoryCounts = disruptedPlans.stream()
                .collect(Collectors.groupingBy(p -> new StoreDateKey(p.storeId(), p.date()), Collectors.counting()));

        final Map<String, String> storeIdToName = invert(storeNameToId);
        final Map<String, String> categoryIdToName = resolveCategoryNames(plans);

        return plans.stream()
                .map(plan -> toReportRow(plan, disruptionsByStore, profiles, actualSales, turnoverTotals, categoryCounts, storeIdToName, categoryIdToName))
                .sorted(Comparator.comparing(AdjustedPlanReportRow::store)
                        .thenComparing(AdjustedPlanReportRow::date)
                        .thenComparing(AdjustedPlanReportRow::category))
                .toList();
    }

    private AdjustedPlanReportRow toReportRow(
            final DailyPlan plan,
            final Map<String, Map<LocalDate, Disruption>> disruptionsByStore,
            final Map<ProfileKey, Map<LocalTime, BigDecimal>> profiles,
            final Map<StoreCategoryDate, Map<LocalTime, BigDecimal>> actualSales,
            final Map<StoreDateKey, BigDecimal> turnoverTotals,
            final Map<StoreDateKey, Long> categoryCounts,
            final Map<String, String> storeIdToName,
            final Map<String, String> categoryIdToName) {

        final Disruption disruption = disruptionsByStore.getOrDefault(plan.storeId(), Map.of()).get(plan.date());
        final BigDecimal adjustedTurnover = disruption == null
                ? plan.turnover()
                : adjustedTurnover(plan, disruption, profiles, actualSales, turnoverTotals, categoryCounts);

        return new AdjustedPlanReportRow(
                plan.date(),
                storeIdToName.getOrDefault(plan.storeId(), plan.storeId()),
                categoryIdToName.getOrDefault(plan.categoryId(), plan.categoryId()),
                plan.turnover(),
                plan.margin(),
                adjustedTurnover);
    }

    private BigDecimal adjustedTurnover(
            final DailyPlan plan,
            final Disruption disruption,
            final Map<ProfileKey, Map<LocalTime, BigDecimal>> profiles,
            final Map<StoreCategoryDate, Map<LocalTime, BigDecimal>> actualSales,
            final Map<StoreDateKey, BigDecimal> turnoverTotals,
            final Map<StoreDateKey, Long> categoryCounts) {

        final Set<LocalTime> disruptedSlots = disruption.algorithmSlots();

        final Map<LocalTime, BigDecimal> shares =
                profiles.getOrDefault(new ProfileKey(plan.storeId(), plan.categoryId(), plan.date().getDayOfWeek()), Map.of());
        if (!disruptedSlots.isEmpty() && shares.isEmpty()) {
            log.info("No intraday profile for store {} / category {} / {} - only actual sales are added back",
                    plan.storeId(), plan.categoryId(), plan.date().getDayOfWeek());
        }
        final BigDecimal sumShare = sumOverSlots(shares, disruptedSlots);

        final Map<LocalTime, BigDecimal> actualSlots =
                actualSales.getOrDefault(new StoreCategoryDate(plan.storeId(), plan.categoryId(), plan.date()), Map.of());
        final BigDecimal actualWithinRange = sumOverSlots(actualSlots, disruptedSlots);

        final StoreDateKey key = new StoreDateKey(plan.storeId(), plan.date());
        final BigDecimal allocatedAmount = allocateAmount(disruption.amount(), plan.turnover(),
                turnoverTotals.get(key), categoryCounts.get(key));

        return adjustedTurnover(plan.turnover(), sumShare, actualWithinRange, allocatedAmount);
    }

    /**
     * {@code adjusted = plan - (sumShare * plan) + actualSales - amount}. {@code sumShare} is
     * clamped to {@code [0, 1]} to guard against noisy pooled shares; a null plan turnover or a
     * null amount is treated as zero. The result is always capped at {@code plan} - this job only
     * ever reduces a plan, so no combination of share, actual sales, and amount may raise it above
     * its original value. Pure function - unit-tested directly.
     */
    static BigDecimal adjustedTurnover(final BigDecimal planTurnover,
                                       final BigDecimal sumShare,
                                       final BigDecimal actualSales,
                                       final BigDecimal amount) {
        final BigDecimal plan = nz(planTurnover);
        final BigDecimal clampedShare = sumShare.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        final BigDecimal lost = plan.multiply(clampedShare, PRECISE_MATH_CONTEXT);
        final BigDecimal computed = plan.subtract(lost).add(actualSales).subtract(nz(amount));
        return computed.min(plan);
    }

    /**
     * Splits a store/date's declared {@code amount} across its categories in proportion to each
     * category's plan turnover, so the per-category deductions sum back to {@code amount}. Falls
     * back to an equal split across {@code categoryCount} when the store/date's total turnover is
     * zero or negative. A null {@code amount} contributes {@code 0}. Pure function - unit-tested
     * directly.
     */
    static BigDecimal allocateAmount(final BigDecimal amount,
                                     final BigDecimal planTurnover,
                                     final BigDecimal totalTurnoverForStoreDate,
                                     final long categoryCount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        final BigDecimal plan = nz(planTurnover);
        final BigDecimal weight = totalTurnoverForStoreDate.signum() > 0
                ? plan.divide(totalTurnoverForStoreDate, PRECISE_MATH_CONTEXT)
                : BigDecimal.ONE.divide(BigDecimal.valueOf(categoryCount), PRECISE_MATH_CONTEXT);
        return amount.multiply(weight, PRECISE_MATH_CONTEXT);
    }

    private static BigDecimal nz(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal sumOverSlots(final Map<LocalTime, BigDecimal> values, final Set<LocalTime> slots) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final LocalTime slot : slots) {
            sum = sum.add(values.getOrDefault(slot, BigDecimal.ZERO));
        }
        return sum;
    }

    /**
     * Adjustment rows whose date falls inside {@code [fromDate, toDate]}. A store whose every row
     * is filtered out here never gets resolved or queried, so it never appears in the report.
     * Pure function - unit-tested directly.
     */
    static List<AdjustmentRequest> withinRange(final List<AdjustmentRequest> adjustments,
                                               final LocalDate fromDate, final LocalDate toDate) {
        return adjustments.stream()
                .filter(a -> !a.date().isBefore(fromDate) && !a.date().isAfter(toDate))
                .toList();
    }

    private Map<String, String> resolveStores(final List<AdjustmentRequest> adjustments) {
        final LocalDate startDate = properties.dateRange().fromDate();
        final Set<String> storeNames = adjustments.stream().map(AdjustmentRequest::storeName).collect(Collectors.toSet());
        final Map<String, String> nameToId = new HashMap<>();
        for (final NamedRef store : locationRepository.findStoresByName(storeNames, startDate)) {
            if (nameToId.putIfAbsent(store.name(), store.id()) != null) {
                log.warn("Store name '{}' resolves to multiple ids; keeping the first ({})", store.name(), nameToId.get(store.name()));
            }
        }
        storeNames.stream()
                .filter(name -> !nameToId.containsKey(name))
                .forEach(name -> log.warn("Store '{}' not found among stores open as of {} - its adjustments are skipped", name, startDate));
        return nameToId;
    }

    private static Map<String, Map<LocalDate, Disruption>> indexDisruptions(
            final List<AdjustmentRequest> adjustments, final Map<String, String> storeNameToId) {
        final Map<String, Map<LocalDate, Disruption>> index = new HashMap<>();
        for (final AdjustmentRequest adjustment : adjustments) {
            final String storeId = storeNameToId.get(adjustment.storeName());
            if (storeId == null) {
                continue;
            }
            index.computeIfAbsent(storeId, _ -> new HashMap<>())
                    .put(adjustment.date(), new Disruption(adjustment.algorithmSlots(), adjustment.amount()));
        }
        return index;
    }

    private Map<String, String> resolveCategoryNames(final List<DailyPlan> plans) {
        final Set<String> categoryIds = plans.stream().map(DailyPlan::categoryId).collect(Collectors.toSet());
        return categoryRepository.findCategoriesById(categoryIds).stream()
                .collect(Collectors.toMap(NamedRef::id, NamedRef::name, (a, _) -> a));
    }

    private static boolean isDisrupted(final Map<String, Map<LocalDate, Disruption>> disruptionsByStore,
                                       final String storeId, final LocalDate date) {
        final Map<LocalDate, Disruption> byDate = disruptionsByStore.get(storeId);
        return byDate != null && byDate.containsKey(date);
    }

    private static Map<String, String> invert(final Map<String, String> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, _) -> a));
    }

    private record Disruption(Set<LocalTime> algorithmSlots, BigDecimal amount) {
    }

    private record StoreDateKey(String storeId, LocalDate date) {
    }
}
