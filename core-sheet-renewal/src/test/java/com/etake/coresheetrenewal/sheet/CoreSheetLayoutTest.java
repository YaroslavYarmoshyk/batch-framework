package com.etake.coresheetrenewal.sheet;

import com.etake.coresheetrenewal.sheet.CoreSheetRow.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreSheetLayoutTest {
    private static final CoreSheetLayout LAYOUT = CoreSheetLayout.parse(
            List.of("Львів 1", "Львів Куліша", "Стрий Центр", "Рівне", "Рівне Базар", "=\"Разом по компанії (\"&COUNTA(A3:A7)&\") маг.\"", "нотатка нижче"),
            List.of("=SUM(AI4:AI5)", "", "29528,2", "=SUM(AI7:AI7)", "", "=SUM(AI3;AI6)"));

    @Test
    void recognisesRowKindsAndStopsAtTheCompanyRow() {
        assertThat(LAYOUT.rows()).extracting(CoreSheetRow::kind)
                .containsExactly(Kind.REGION, Kind.STORE, Kind.STORE, Kind.REGION, Kind.STORE, Kind.COMPANY);
        assertThat(LAYOUT.rows()).extracting(CoreSheetRow::number).containsExactly(3, 4, 5, 6, 7, 8);
        assertThat(LAYOUT.lastRowNumber()).isEqualTo(8);
    }

    @Test
    void aRegionAddsUpTheStoresBelowIt() {
        assertThat(LAYOUT.storesOf(LAYOUT.rows().getFirst())).extracting(CoreSheetRow::name)
                .containsExactly("Львів Куліша", "Стрий Центр");
        assertThat(LAYOUT.storesOf(LAYOUT.rows().get(3))).extracting(CoreSheetRow::name)
                .containsExactly("Рівне Базар");
    }

    @Test
    void theCompanyAddsUpAllStores() {
        assertThat(LAYOUT.storesOf(LAYOUT.rows().getLast())).hasSize(3);
    }

    @Test
    void aSheetWithoutACompanyRowIsRejected() {
        assertThatThrownBy(() -> CoreSheetLayout.parse(List.of("Львів 1"), List.of("=SUM(AI4:AI5)")))
                .isInstanceOf(IllegalStateException.class);
    }
}
