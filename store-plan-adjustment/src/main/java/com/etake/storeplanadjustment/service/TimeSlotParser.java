package com.etake.storeplanadjustment.service;

import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.etake.storeplanadjustment.utils.Constants.SLOT_MINUTES;
import static com.etake.storeplanadjustment.utils.Constants.SLOTS_PER_DAY;

/**
 * Turns the {@code time_ranges} cell ({@code "19:00-20:00;21:34-22:10"} or {@code "all day"}) into
 * the set of 30-minute slots (keyed by slot start time) it covers.
 *
 * <p>Endpoints are rounded to the nearest 30 minutes ({@code 19:10 -> 19:00}, {@code 19:20 -> 19:30})
 * to line up with the aggregated transaction data. A range covers every slot in
 * {@code [roundedStart, roundedEnd)}. Stateless and side-effect free, so it is unit-tested directly.
 */
@Component
public class TimeSlotParser {
    private static final String ALL_DAY = "all day";
    private static final String RANGE_SEPARATOR = ";";
    private static final String BOUND_SEPARATOR = "-";
    private static final int MINUTES_PER_DAY = SLOTS_PER_DAY * SLOT_MINUTES;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    /**
     * @return the covered slots; empty when the cell has no ranges at all
     * @throws IllegalArgumentException when any range is invalid; the message names every invalid
     *                                  range and why, in Ukrainian, for the invalid rows report
     */
    public Set<LocalTime> parse(final String timeRanges) {
        if (timeRanges == null || timeRanges.isBlank()) {
            return Set.of();
        }
        final String trimmed = timeRanges.trim();
        if (trimmed.equalsIgnoreCase(ALL_DAY)) {
            return allDaySlots();
        }

        final Set<LocalTime> slots = new LinkedHashSet<>();
        final List<String> errors = new ArrayList<>();
        for (final String segment : trimmed.split(RANGE_SEPARATOR)) {
            slots.addAll(parseRange(segment.trim(), errors));
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return slots;
    }

    private Set<LocalTime> parseRange(final String segment, final List<String> errors) {
        if (segment.isBlank()) {
            return Set.of();
        }
        final String[] bounds = segment.split(BOUND_SEPARATOR);
        if (bounds.length != 2) {
            errors.add("Некоректний часовий проміжок '%s': очікується, наприклад, 19:00-20:00 або all day".formatted(segment));
            return Set.of();
        }

        final LocalTime start;
        final LocalTime end;
        try {
            start = LocalTime.parse(bounds[0].trim(), TIME_FORMAT);
            end = LocalTime.parse(bounds[1].trim(), TIME_FORMAT);
        } catch (final RuntimeException e) {
            errors.add("Некоректний час у проміжку '%s': очікується, наприклад, 19:00-20:00".formatted(segment));
            return Set.of();
        }

        if (!end.isAfter(start)) {
            errors.add("Некоректний часовий проміжок '%s': кінець має бути пізніше за початок (перехід через північ не підтримується)".formatted(segment));
            return Set.of();
        }

        final int roundedStart = roundToSlot(start);
        final int roundedEnd = roundToSlot(end);

        final Set<LocalTime> slots = new LinkedHashSet<>();
        for (int minute = roundedStart; minute < roundedEnd; minute += SLOT_MINUTES) {
            slots.add(toLocalTime(minute));
        }
        if (slots.isEmpty()) {
            // Range collapses to nothing after rounding (e.g. 19:10-19:14): keep the slot the start falls in.
            slots.add(toLocalTime(floorToSlot(start)));
        }
        return slots;
    }

    private static Set<LocalTime> allDaySlots() {
        final Set<LocalTime> slots = new LinkedHashSet<>();
        for (int minute = 0; minute < MINUTES_PER_DAY; minute += SLOT_MINUTES) {
            slots.add(toLocalTime(minute));
        }
        return slots;
    }

    private static int roundToSlot(final LocalTime time) {
        final int totalMinutes = time.getHour() * 60 + time.getMinute();
        return (int) (Math.round(totalMinutes / (double) SLOT_MINUTES) * SLOT_MINUTES);
    }

    private static int floorToSlot(final LocalTime time) {
        final int totalMinutes = time.getHour() * 60 + time.getMinute();
        return (totalMinutes / SLOT_MINUTES) * SLOT_MINUTES;
    }

    private static LocalTime toLocalTime(final int minuteOfDay) {
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
    }
}
