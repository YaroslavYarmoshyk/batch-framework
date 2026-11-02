package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.model.AbcCategory;
import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StoreChecks;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyTotalsServiceTest {
    private final MonthlyTotalsService service = new MonthlyTotalsService();

    private static MonthlyTotals totals(final String margin, final long checks, final String amount,
                                        final Map<AbcCategory, BigDecimal> stock) {
        return new MonthlyTotals(new BigDecimal(margin),
                new StoreChecks("Магазин", checks, new BigDecimal(amount), checks * 2, checks, new BigDecimal(amount)), stock);
    }

    @Test
    void addsUpMarginChecksAndStockByCategory() {
        final MonthlyTotals sum = service.sum("Регіон", List.of(
                totals("10", 100, "30000", Map.of(AbcCategory.A, BigDecimal.ONE, AbcCategory.D, BigDecimal.TWO)),
                totals("5", 300, "30000", Map.of(AbcCategory.A, BigDecimal.TEN))));

        assertThat(sum.margin()).isEqualByComparingTo("15");
        assertThat(sum.checks().storeName()).isEqualTo("Регіон");
        assertThat(sum.checks().checks()).isEqualTo(400);
        assertThat(sum.checks().amount()).isEqualByComparingTo("60000");
        assertThat(sum.checks().positions()).isEqualTo(800);
        assertThat(sum.checks().loyaltyChecks()).isEqualTo(400);
        assertThat(sum.checks().loyaltyAmount()).isEqualByComparingTo("60000");
        assertThat(sum.stock().get(AbcCategory.A)).isEqualByComparingTo("11");
        assertThat(sum.stock().get(AbcCategory.D)).isEqualByComparingTo("2");
    }

    @Test
    void theSumOfNothingIsEmpty() {
        final MonthlyTotals sum = service.sum("Регіон", List.of());

        assertThat(sum.margin()).isEqualByComparingTo("0");
        assertThat(sum.checks().checks()).isZero();
        assertThat(sum.stock()).isEmpty();
    }
}
