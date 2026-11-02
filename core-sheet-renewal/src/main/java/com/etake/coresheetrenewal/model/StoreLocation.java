package com.etake.coresheetrenewal.model;

import java.time.LocalDate;

/**
 * @param regionName {@code null} when the store is not assigned to a region
 * @param closeDate  {@code null} while the store is open
 */
public record StoreLocation(String storeName, String regionName, LocalDate closeDate) {

    public boolean closedBefore(final LocalDate date) {
        return closeDate != null && closeDate.isBefore(date);
    }

    /** Of two locations sharing a name, a closed store and its reopened namesake, the one open the longest is current. */
    public StoreLocation latest(final StoreLocation other) {
        if (closeDate == null) {
            return this;
        }
        return other.closeDate == null || other.closeDate.isAfter(closeDate) ? other : this;
    }
}
