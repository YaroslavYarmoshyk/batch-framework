package com.etake.coresheetrenewal.sheet;

/** @param number 1-based row number */
public record CoreSheetRow(int number, Kind kind, String name) {

    public enum Kind {
        STORE, REGION, COMPANY
    }
}
