package com.etake.promotionforecast.writer.googlesheets;

import com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.TotalColumn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Expected formulas are the ones of the reference spreadsheet, with columns I–L of the cyclic-action template removed. */
class LayoutFormulasTest {
    private static final String MANAGER_SHEET = "INDIRECT(\"'\"&SUBSTITUTE(A2;\"'\";\"''\")&\"'!%s\")";

    private final LayoutFormulas formulas = LayoutFormulas.forLocale("ru_RU");

    @Test
    void separatorFollowsSpreadsheetLocale() {
        assertThat(LayoutFormulas.forLocale("uk_UA").separator()).isEqualTo(";");
        assertThat(LayoutFormulas.forLocale("ru_RU").separator()).isEqualTo(";");
        assertThat(LayoutFormulas.forLocale("de_DE").separator()).isEqualTo(";");
        assertThat(LayoutFormulas.forLocale("en_US").separator()).isEqualTo(",");
        assertThat(LayoutFormulas.forLocale("en_GB").separator()).isEqualTo(",");
        assertThat(LayoutFormulas.forLocale(null).separator()).isEqualTo(",");
    }

    @Test
    void adjustedQty() {
        // Reference: =IFERROR($N2/SUMIF($F:$F;$F2;$N:$N)*VLOOKUP($F2;'Прогноз'!$A:$F;6;0);"")
        assertThat(formulas.adjustedQty())
                .isEqualTo("=IFERROR(J2/SUMIF(F:F;F2;J:J)*VLOOKUP(F2;'Прогноз'!A:F;6;0);\"\")");
    }

    @Test
    void managerQty() {
        // Reference: =IFERROR(SUMIFS(INDIRECT("'"&$A2&"'!P:P");INDIRECT("'"&$A2&"'!F:F");$F2;INDIRECT("'"&$A2&"'!B:B");$B2);$N2)
        assertThat(formulas.managerQty()).isEqualTo("=IFERROR(SUMIFS("
                + sheet("L:L") + ";" + sheet("F:F") + ";F2;" + sheet("B:B") + ";B2);J2)");
    }

    @Test
    void managerFlags() {
        assertThat(formulas.managerFlag(TotalColumn.INCLUDE_STOCK)).isEqualTo("=IF(COUNTIFS("
                + sheet("M:M") + ";\"Так\";" + sheet("F:F") + ";F2;" + sheet("B:B") + ";B2)=1;\"Так\";\"Ні\")");
        assertThat(formulas.managerFlag(TotalColumn.INCLUDE_MULTIPLICITY)).startsWith("=IF(COUNTIFS(" + sheet("O:O") + ";\"Так\";");
    }

    @Test
    void firstShipment() {
        assertThat(formulas.firstShipment()).isEqualTo("=IF(COUNTIFS("
                + sheet("P:P") + ";70%;" + sheet("F:F") + ";F2;" + sheet("B:B") + ";B2)=1;70%;100%)");
    }

    @Test
    void multiplicityAndForecastSheet() {
        assertThat(formulas.multiplicity()).isEqualTo("=IFERROR(VLOOKUP(F2;'Прогноз'!A:G;7;0);\"\")");
        assertThat(formulas.analystForecast()).isEqualTo("=SUMIF('Загальна акція'!F:F;A2;'Загальна акція'!J:J)");
        assertThat(formulas.deviation()).isEqualTo("=IFERROR(C2/D2-1;\"\")");
    }

    @Test
    void usesCommasForCommaLocales() {
        assertThat(LayoutFormulas.forLocale("en_US").adjustedQty())
                .isEqualTo("=IFERROR(J2/SUMIF(F:F,F2,J:J)*VLOOKUP(F2,'Прогноз'!A:F,6,0),\"\")");
    }

    private static String sheet(final String columns) {
        return MANAGER_SHEET.formatted(columns);
    }
}
