package com.etake.storeplanadjustment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvalidRowColumn {
    ROW_NUMBER("Рядок у файлі", 0, 14),
    STORE("Магазин", 1, 25),
    DATE("Дата", 2, 14),
    TIME_RANGES("Часові проміжки", 3, 25),
    AMOUNT("Сума", 4, 12),
    REASON("Причина помилки", 5, 90);

    private final String header;
    private final int index;
    private final int widthChars;
}
