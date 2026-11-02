package com.etake.coresheetrenewal.sheet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryFormulasTest {
    /** Rows: 3 Північ, 4-5 stores, 6 Шептицький, 7 store, 8 company. */
    private static final CoreSheetLayout LAYOUT = CoreSheetLayout.parse(
            List.of("Північ", "А", "Б", "Шептицький", "В", "=\"Разом\""),
            List.of("=SUM(AI4:AI5)", "", "", "=SUM(AI7:AI7)", "", "=SUM(AI3;AI6)"));
    private static final CoreSheetRow REGION = LAYOUT.rows().get(3);
    private static final CoreSheetRow COMPANY = LAYOUT.rows().getLast();

    private static String region(final String formula) {
        return SummaryFormulas.rewrite(formula, REGION, LAYOUT);
    }

    private static String company(final String formula) {
        return SummaryFormulas.rewrite(formula, COMPANY, LAYOUT);
    }

    @Test
    void regionRowsAddUpTheirStoresAndReadTheirOwnCells() {
        assertThat(region("=SUM(C4:C14)")).isEqualTo("=SUM(C7:C7)");
        assertThat(region("=IFERROR(ROUND(D3/C3;4);\"\")")).isEqualTo("=IFERROR(ROUND(D6/C6;4);\"\")");
        assertThat(region("=IFERROR(L3/AVERAGE(M4:M14);\"\")")).isEqualTo("=IFERROR(L6/AVERAGE(M7:M7);\"\")");
        assertThat(region("=SUMPRODUCT((T4:T14<>\"Не працював\")*1;X4:X14)/T3-1"))
                .isEqualTo("=SUMPRODUCT((T7:T7<>\"Не працював\")*1;X7:X7)/T6-1");
        assertThat(region("=IFERROR(SUMPRODUCT((BN4:BN14<>\"\")*1;X4:X14)/BN3;\"\")"))
                .isEqualTo("=IFERROR(SUMPRODUCT((BN7:BN7<>\"\")*1;X7:X7)/BN6;\"\")");
    }

    @Test
    void theCompanyRowAddsUpTheRegions() {
        assertThat(company("=SUM(C3;C15;C24)")).isEqualTo("=SUM(C3;C6)");
        assertThat(company("=IFERROR(ROUND(D75/C75;4);\"\")")).isEqualTo("=IFERROR(ROUND(D8/C8;4);\"\")");
    }

    @Test
    void theCompanyRowAveragesOverAllStoreBlocksRepairingBrokenRanges() {
        assertThat(company("=IFERROR(R75/AVERAGE(S11:S14;S5:S23;S27:S33);\"\")"))
                .isEqualTo("=IFERROR(R8/AVERAGE(S4:S5;S7:S7);\"\")");
    }

    @Test
    void theCompanyRowFiltersOverAllRowsWithCurrentRegionNames() {
        assertThat(company("=SUM(FILTER(X3:X74;T3:T74<>\"Не працював\";NOT(REGEXMATCH(A3:A74;\"Львів 1|^Рівне$\"))))/T75-1"))
                .isEqualTo("=SUM(FILTER(X3:X7;T3:T7<>\"Не працював\";NOT(REGEXMATCH(A3:A7;\"^(Північ|Шептицький)$\"))))/T8-1");
    }

    @Test
    void theStoreCountInTheCompanyNameCoversCurrentRowsAndRegions() {
        assertThat(company("=\"Разом по компанії (\"&COUNTA(FILTER($A$3:$A$74;NOT(REGEXMATCH($A$3:$A$74;\"Львів 1|^Рівне$\"))))&\") маг.\""))
                .isEqualTo("=\"Разом по компанії (\"&COUNTA(FILTER($A$3:$A$7;NOT(REGEXMATCH($A$3:$A$7;\"^(Північ|Шептицький)$\"))))&\") маг.\"");
    }
}
