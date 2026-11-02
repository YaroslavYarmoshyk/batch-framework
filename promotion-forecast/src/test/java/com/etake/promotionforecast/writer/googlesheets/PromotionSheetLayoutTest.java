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

    /** These indexes are hardcoded in TOTAL_COLUMN of apps-script/promotion.gs (1C export) — update both together. */
    @Test
    void columnsReadByTheAppsScriptExportStayInPlace() {
        assertThat(TotalColumn.MANAGER.index()).isEqualTo(0);
        assertThat(TotalColumn.STORE.index()).isEqualTo(1);
        assertThat(TotalColumn.PRODUCT_ID.index()).isEqualTo(5);
        assertThat(TotalColumn.PRODUCT_NAME.index()).isEqualTo(6);
        assertThat(TotalColumn.MANAGER_QTY.index()).isEqualTo(11);
        assertThat(TotalColumn.INCLUDE_STOCK.index()).isEqualTo(12);
        assertThat(TotalColumn.MULTIPLICITY.index()).isEqualTo(13);
        assertThat(TotalColumn.INCLUDE_MULTIPLICITY.index()).isEqualTo(14);
        assertThat(TotalColumn.FIRST_SHIPMENT.index()).isEqualTo(15);
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
