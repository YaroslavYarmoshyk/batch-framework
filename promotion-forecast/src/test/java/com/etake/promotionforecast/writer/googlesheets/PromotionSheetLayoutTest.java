package com.etake.promotionforecast.writer.googlesheets;

import com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.TotalColumn;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionSheetLayoutTest {

    /** The reference «Загальна акція» without the cyclic-action columns I–L (СДП до акції … Кеф. динаміки СДП). */
    @Test
    void matchesReferenceTemplate() {
        assertThat(Arrays.stream(TotalColumn.values()).map(TotalColumn::getHeader)).containsExactly(
                "Менеджер", "Магазин", "Формат", "Перехідний залишок", "Підкатегорія3", "Код", "Номенклатура",
                "Алгоритм", "СДП прогнозовані", "Прогнозовані продажі", "Прогнозовані продажі кореговані",
                "Прогнозовані продажі менеджер", "Чи враховувати залишок на магазині", "Кратність",
                "Чи враховувати кратність на магазині", "Перша відправка, %");
    }

    @Test
    void convertsColumnIndexesToLetters() {
        assertThat(PromotionSheetLayout.letter(0)).isEqualTo("A");
        assertThat(PromotionSheetLayout.letter(25)).isEqualTo("Z");
        assertThat(PromotionSheetLayout.letter(26)).isEqualTo("AA");
        assertThat(PromotionSheetLayout.letter(51)).isEqualTo("AZ");
        assertThat(PromotionSheetLayout.letter(52)).isEqualTo("BA");
        assertThat(PromotionSheetLayout.letter(701)).isEqualTo("ZZ");
        assertThat(PromotionSheetLayout.letter(702)).isEqualTo("AAA");
    }
}
