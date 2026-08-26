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
 * Orchestrates the adjustment: resolve stores, pick the plans to adjust, build intraday profiles,
 * read the actual sales inside each disrupted window, and apply
 * <pre>adjusted = plan - (Σ share within range) * plan + (actual sales within range)</pre>
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

        final Map<String, String> storeNameToId = resolveStores(adjustments);

        // storeId -> date -> disrupted slots; and storeId -> all disrupted dates (for profile exclusion).
        final Map<String, Map<LocalDate, Set<LocalTime>>> disruptionsByStore = indexDisruptions(adjustments, storeNameToId);
        final Map<String, Set<LocalDate>> disruptedDatesByStore = disruptionsByStore.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().keySet()));

        if (disruptionsByStore.isEmpty()) {
            log.warn("No adjustments matched an open store");
            return List.of();
        }
        final Set<String> storeIds = disruptionsByStore.keySet();

        final List<DailyPlan> plans = dailyPlanRepository.findPlansToAdjust(fromDate, toDate, storeIds).stream()
                .filter(plan -> isDisrupted(disruptionsByStore, plan.storeId(), plan.date()))
                .toList();
        if (plans.isEmpty()) {
            log.warn("No daily plans in [{} .. {}] match the disrupted store/date combinations", fromDate, toDate);
            return List.of();
        }

        final Set<DayOfWeek> weekdays = plans.stream().map(p -> p.date().getDayOfWeek()).collect(Collectors.toSet());
        final Map<ProfileKey, Map<LocalTime, BigDecimal>> profiles =
                shareProfileService.buildProfiles(fromDate, weekdays, storeIds, disruptedDatesByStore);

        final Set<LocalDate> planDates = plans.stream().map(DailyPlan::date).collect(Collectors.toSet());
        final Map<StoreCategoryDate, Map<LocalTime, BigDecimal>> actualSales = SlotTurnovers.groupByStoreCategoryDate(
                transactionSlotRepository.findSlotTurnover(planDates, storeIds));

        final Map<String, String> storeIdToName = invert(storeNameToId);
        final Map<String, String> categoryIdToName = resolveCategoryNames(plans);

        return plans.stream()
                .map(plan -> toReportRow(plan, disruptionsByStore, profiles, actualSales, storeIdToName, categoryIdToName))
                .sorted(Comparator.comparing(AdjustedPlanReportRow::store)
                        .thenComparing(AdjustedPlanReportRow::date)
                        .thenComparing(AdjustedPlanReportRow::category))
                .toList();
    }

    private AdjustedPlanReportRow toReportRow(
            final DailyPlan plan,
            final Map<String, Map<LocalDate, Set<LocalTime>>> disruptionsByStore,
            final Map<ProfileKey, Map<LocalTime, BigDecimal>> profiles,
            final Map<StoreCategoryDate, Map<LocalTime, BigDecimal>> actualSales,
            final Map<String, String> storeIdToName,
            final Map<String, String> categoryIdToName) {

        final Set<LocalTime> disruptedSlots = disruptionsByStore.get(plan.storeId()).get(plan.date());

        final Map<LocalTime, BigDecimal> shares =
                profiles.getOrDefault(new ProfileKey(plan.storeId(), plan.categoryId(), plan.date().getDayOfWeek()), Map.of());
        if (shares.isEmpty()) {
            log.info("No intraday profile for store {} / category {} / {} - only actual sales are added back",
                    plan.storeId(), plan.categoryId(), plan.date().getDayOfWeek());
        }
        final BigDecimal sumShare = sumOverSlots(shares, disruptedSlots);

        final Map<LocalTime, BigDecimal> actualSlots =
                actualSales.getOrDefault(new StoreCategoryDate(plan.storeId(), plan.categoryId(), plan.date()), Map.of());
        final BigDecimal actualWithinRange = sumOverSlots(actualSlots, disruptedSlots);

        final BigDecimal adjustedTurnover = adjustedTurnover(plan.turnover(), sumShare, actualWithinRange);

        return new AdjustedPlanReportRow(
                plan.date(),
                storeIdToName.getOrDefault(plan.storeId(), plan.storeId()),
                categoryIdToName.getOrDefault(plan.categoryId(), plan.categoryId()),
                plan.turnover(),
                plan.margin(),
                adjustedTurnover);
    }

    /**
     * {@code adjusted = plan - (sumShare * plan) + actualSales}. {@code sumShare} is clamped to
     * {@code [0, 1]} to guard against noisy pooled shares; a null plan turnover is treated as zero.
     * Pure function - unit-tested directly.
     */
    static BigDecimal adjustedTurnover(final BigDecimal planTurnover,
                                       final BigDecimal sumShare,
                                       final BigDecimal actualSales) {
        final BigDecimal plan = planTurnover == null ? BigDecimal.ZERO : planTurnover;
        final BigDecimal clampedShare = sumShare.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        final BigDecimal lost = plan.multiply(clampedShare, PRECISE_MATH_CONTEXT);
        return plan.subtract(lost).add(actualSales);
    }

    private static BigDecimal sumOverSlots(final Map<LocalTime, BigDecimal> values, final Set<LocalTime> slots) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final LocalTime slot : slots) {
            sum = sum.add(values.getOrDefault(slot, BigDecimal.ZERO));
        }
        return sum;
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

    private static Map<String, Map<LocalDate, Set<LocalTime>>> indexDisruptions(
            final List<AdjustmentRequest> adjustments, final Map<String, String> storeNameToId) {
        final Map<String, Map<LocalDate, Set<LocalTime>>> index = new HashMap<>();
        for (final AdjustmentRequest adjustment : adjustments) {
            final String storeId = storeNameToId.get(adjustment.storeName());
            if (storeId == null) {
                continue;
            }
            index.computeIfAbsent(storeId, k -> new HashMap<>()).put(adjustment.date(), adjustment.slots());
        }
        return index;
    }

    private Map<String, String> resolveCategoryNames(final List<DailyPlan> plans) {
        final Set<String> categoryIds = plans.stream().map(DailyPlan::categoryId).collect(Collectors.toSet());
        return categoryRepository.findCategoriesById(categoryIds).stream()
                .collect(Collectors.toMap(NamedRef::id, NamedRef::name, (a, b) -> a));
    }

    private static boolean isDisrupted(final Map<String, Map<LocalDate, Set<LocalTime>>> disruptionsByStore,
                                       final String storeId, final LocalDate date) {
        final Map<LocalDate, Set<LocalTime>> byDate = disruptionsByStore.get(storeId);
        return byDate != null && byDate.containsKey(date);
    }

    private static Map<String, String> invert(final Map<String, String> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));
    }
}
