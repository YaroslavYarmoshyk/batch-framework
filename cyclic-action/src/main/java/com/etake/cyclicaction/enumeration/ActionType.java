package com.etake.cyclicaction.enumeration;

import lombok.Getter;

import java.util.Optional;

public enum ActionType {
    CYCLIC("Циклічна акція");

    @Getter
    private final String type;

    ActionType(final String type) {
        this.type = type;
    }

    public static boolean isCyclic(final String type) {
        return Optional.ofNullable(type)
                .map(t -> t.equals(ActionType.CYCLIC.getType()))
                .orElse(false);
    }
}
