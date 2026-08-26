package com.etake.storeplanadjustment.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSlotParserTest {
    private final TimeSlotParser parser = new TimeSlotParser();

    @Test
    void roundsEndpointsToNearestHalfHour() {
        // 19:20 -> 19:30 (start), 19:50 -> 20:00 (end) => only the 19:30 slot
        assertEquals(Set.of(LocalTime.of(19, 30)), parser.parse("19:20-19:50"));
    }

    @Test
    void roundsStartDownAndExpandsRange() {
        // 19:10 -> 19:00, 20:00 stays => slots 19:00 and 19:30
        assertEquals(Set.of(LocalTime.of(19, 0), LocalTime.of(19, 30)), parser.parse("19:10-20:00"));
    }

    @Test
    void unionsMultipleSemicolonSeparatedRanges() {
        // 21:34 -> 21:30, 22:10 -> 22:00 => 21:30 slot
        assertEquals(
                Set.of(LocalTime.of(19, 0), LocalTime.of(19, 30), LocalTime.of(21, 30)),
                parser.parse("19:00-20:00;21:34-22:10"));
    }

    @Test
    void allDayExpandsToEveryHalfHourSlot() {
        final Set<LocalTime> slots = parser.parse("all day");
        assertEquals(48, slots.size());
        assertTrue(slots.contains(LocalTime.MIDNIGHT));
        assertTrue(slots.contains(LocalTime.of(23, 30)));
    }

    @Test
    void allDayIsCaseInsensitive() {
        assertEquals(48, parser.parse("All Day").size());
    }

    @Test
    void degenerateRangeKeepsSlotContainingStart() {
        // Both endpoints round to 19:00, leaving an empty span -> keep the slot 19:10 falls in.
        assertEquals(Set.of(LocalTime.of(19, 0)), parser.parse("19:10-19:14"));
    }

    @Test
    void overnightRangeIsSkipped() {
        assertTrue(parser.parse("23:00-01:00").isEmpty());
    }

    @Test
    void blankAndNullProduceNoSlots() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
    }

    @Test
    void malformedRangeIsSkippedButOthersKept() {
        assertEquals(Set.of(LocalTime.of(8, 0)), parser.parse("garbage;08:00-08:30"));
    }
}
