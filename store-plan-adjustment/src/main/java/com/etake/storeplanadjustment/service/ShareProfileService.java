package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.model.ProfileKey;
import com.etake.storeplanadjustment.model.SlotTurnover;
import com.etake.storeplanadjustment.model.StoreCategoryDate;
import com.etake.storeplanadjustment.repository.TransactionSlotRepository;
import com.etake.storeplanadjustment.utils.SlotTurnovers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.etake.storeplanadjustment.utils.Constants.PRECISE_MATH_CONTEXT;
import static com.etake.storeplanadjustment.utils.Constants.PROFILE_WEEKS;

/**
 * Builds the intraday turnover share profile for each store/category/weekday from the
 * {@value com.etake.storeplanadjustment.utils.Constants#PROFILE_WEEKS} most recent occurrences of
 * that weekday before the run's start date, using a pooled ratio:
 * {@code share[slot] = Σ slot turnover / Σ whole-day turnover} over those dates.
 *
 * <p>Dates listed in the input file as disrupted are excluded per store, so a "broken" day does not
 * pollute that store's profile (averaging over whatever dates remain; no backfill).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareProfileService {
    private final TransactionSlotRepository transactionSlotRepository;

    public Map<ProfileKey, Map<LocalTime, BigDecimal>> buildProfiles(
            final LocalDate startDate,
            final Set<DayOfWeek> weekdays,
            final Collection<String> storeIds,
            final Map<String, Set<LocalDate>> disruptedDatesByStore) {

        final Set<LocalDate> candidateDates = candidateDates(startDate, weekdays);
        if (candidateDates.isEmpty() || storeIds.isEmpty()) {
            return Map.of();
        }

        final var slotData = transactionSlotRepository.findSlotTurnover(candidateDates, storeIds);
        return aggregate(slotData, disruptedDatesByStore);
    }

    /**
     * For every weekday in use, the {@value PROFILE_WEEKS} most recent dates with that weekday
     * strictly before {@code startDate}.
     */
    private static Set<LocalDate> candidateDates(final LocalDate startDate, final Set<DayOfWeek> weekdays) {
        final Set<LocalDate> dates = new LinkedHashSet<>();
        for (final DayOfWeek weekday : weekdays) {
            LocalDate date = startDate.minusDays(1);
            while (date.getDayOfWeek() != weekday) {
                date = date.minusDays(1);
            }
            for (int i = 0; i < PROFILE_WEEKS; i++) {
                dates.add(date);
                date = date.minusWeeks(1);
            }
        }
        return dates;
    }

    private static Map<ProfileKey, Map<LocalTime, BigDecimal>> aggregate(
            final java.util.List<SlotTurnover> slotData,
            final Map<String, Set<LocalDate>> disruptedDatesByStore) {

        // Pool slot turnover and whole-day turnover across the qualifying dates of each profile key.
        final Map<ProfileKey, Map<LocalTime, BigDecimal>> slotSums = new HashMap<>();
        final Map<ProfileKey, BigDecimal> dayTotals = new HashMap<>();

        SlotTurnovers.groupByStoreCategoryDate(slotData).forEach((scd, slots) -> {
            if (isDisrupted(scd, disruptedDatesByStore)) {
                return;
            }
            final ProfileKey key = new ProfileKey(scd.storeId(), scd.categoryId(), scd.date().getDayOfWeek());
            final Map<LocalTime, BigDecimal> keySlots = slotSums.computeIfAbsent(key, k -> new HashMap<>());
            slots.forEach((slot, turnover) -> {
                keySlots.merge(slot, turnover, BigDecimal::add);
                dayTotals.merge(key, turnover, BigDecimal::add);
            });
        });

        final Map<ProfileKey, Map<LocalTime, BigDecimal>> profiles = new HashMap<>();
        slotSums.forEach((key, slots) -> {
            final BigDecimal total = dayTotals.get(key);
            if (total == null || total.signum() == 0) {
                return;
            }
            final Map<LocalTime, BigDecimal> shares = new HashMap<>();
            slots.forEach((slot, sum) -> shares.put(slot, sum.divide(total, PRECISE_MATH_CONTEXT)));
            profiles.put(key, shares);
        });
        return profiles;
    }

    private static boolean isDisrupted(final StoreCategoryDate scd,
                                       final Map<String, Set<LocalDate>> disruptedDatesByStore) {
        final Set<LocalDate> disrupted = disruptedDatesByStore.get(scd.storeId());
        return disrupted != null && disrupted.contains(scd.date());
    }
}
