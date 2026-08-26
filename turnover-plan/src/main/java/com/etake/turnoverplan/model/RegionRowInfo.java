package com.etake.turnoverplan.model;

public record RegionRowInfo(
        String regionName,
        Integer regionRowIndex,
        Integer regionStoresStartRowIndex,
        Integer regionStoresEndRowIndex
) {
}
