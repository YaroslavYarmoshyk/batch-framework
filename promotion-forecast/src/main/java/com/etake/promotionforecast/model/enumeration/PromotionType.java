package com.etake.promotionforecast.model.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PromotionType {
    CYCLIC("Циклічна акція");

    private final String name;
}
