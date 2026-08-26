package com.etake.storeplanadjustment.model;

/**
 * Generic id/name pair used for resolving store and category lookups. Repositories alias their
 * columns to {@code id} / {@code name} so this single record serves both.
 */
public record NamedRef(
        String id,
        String name
) {
}
