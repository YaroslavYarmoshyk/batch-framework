package com.etake.avgcheckplan.model;

/**
 * Generic id/name pair used for resolving store lookups against {@code locations}.
 */
public record NamedRef(
        String id,
        String name
) {
}
