package com.etake.cyclicforecast.model;

public enum AlgorithmLevel {
    LEVEL_1(1),
    LEVEL_2(2),
    LEVEL_3(3),
    LEVEL_4(4),
    LEVEL_5(5),
    NONE(0);

    private final int number;

    AlgorithmLevel(final int number) {
        this.number = number;
    }

    public String display() {
        return this == NONE ? "NONE" : String.valueOf(number);
    }
}
