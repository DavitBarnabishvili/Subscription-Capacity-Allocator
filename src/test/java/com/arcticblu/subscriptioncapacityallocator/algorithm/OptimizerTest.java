package com.arcticblu.subscriptioncapacityallocator.algorithm;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import java.time.Duration;

public class OptimizerTest {
    private final Optimizer optimizer = new Optimizer();

    @Test
    void emptyCandidateList_returnsEmptyResult() {
        OptimalResult result = optimizer.optimize(new BigDecimal("100"), List.of());
        
        assertThat(result.finalSubscriptions()).isEmpty();
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("0");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void zeroCapacity_rejectsEverything() {
        List<Subscription> candidates = List.of(
                new Subscription("A", new BigDecimal("5"), new BigDecimal("100")));

        OptimalResult result = optimizer.optimize(BigDecimal.ZERO, candidates);

        assertThat(result.finalSubscriptions()).isEmpty();
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void singleItem_fittingExactly_isAccepted() {
        Subscription a = new Subscription("A", new BigDecimal("10"), new BigDecimal("50"));

        OptimalResult result = optimizer.optimize(new BigDecimal("10"), List.of(a));

        assertThat(result.finalSubscriptions()).containsExactly(a);
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("10");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("50");
    }

    @Test
    void singleItem_exceedingCapacity_isRejected() {
        Subscription a = new Subscription("A", new BigDecimal("20"), new BigDecimal("50"));

        OptimalResult result = optimizer.optimize(new BigDecimal("10"), List.of(a));

        assertThat(result.finalSubscriptions()).isEmpty();
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("0");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void everyRequestExceedsCapacityIndividually_resultIsEmpty() {
        List<Subscription> candidates = List.of(
                new Subscription("A", new BigDecimal("50"), new BigDecimal("500")),
                new Subscription("B", new BigDecimal("60"), new BigDecimal("700")));

        OptimalResult result = optimizer.optimize(new BigDecimal("15"), candidates);

        assertThat(result.finalSubscriptions()).isEmpty();
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("0");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void allItemsFitWithinCapacity_allAreAccepted() {
        List<Subscription> candidates = List.of(
                new Subscription("A", new BigDecimal("3"), new BigDecimal("30")),
                new Subscription("B", new BigDecimal("4"), new BigDecimal("40")));

        OptimalResult result = optimizer.optimize(new BigDecimal("100"), candidates);

        assertThat(result.finalSubscriptions()).containsExactlyInAnyOrderElementsOf(candidates);
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("7");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("70");
    }

    @Test
    void picksHigherValueCombination_overSingleHighValueItem() {
        Subscription a = new Subscription("A", new BigDecimal("10"), new BigDecimal("100"));
        Subscription b = new Subscription("B", new BigDecimal("5"), new BigDecimal("60"));
        Subscription c = new Subscription("C", new BigDecimal("5"), new BigDecimal("60"));

        OptimalResult result = optimizer.optimize(new BigDecimal("13"), List.of(a, b, c));

        assertThat(result.finalSubscriptions()).containsExactlyInAnyOrder(b, c);
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("120");
    }

    @Test
    void tiedOptimalCombinations_totalsAreCorrectRegardlessOfChosenSubset() {
        Subscription a = new Subscription("A", new BigDecimal("10"), new BigDecimal("100"));
        Subscription b = new Subscription("B", new BigDecimal("5"), new BigDecimal("50"));
        Subscription c = new Subscription("C", new BigDecimal("5"), new BigDecimal("50"));

        OptimalResult result = optimizer.optimize(new BigDecimal("10"), List.of(a, b, c));

        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("100");
        assertThat(result.totalRequestedAmount()).isLessThanOrEqualTo(new BigDecimal("10"));
    }

    @Test
    void assignmentExample() {
        Subscription a = new Subscription("Investor A", new BigDecimal("5"), new BigDecimal("120"));
        Subscription b = new Subscription("Investor B", new BigDecimal("10"), new BigDecimal("200"));
        Subscription c = new Subscription("Investor C", new BigDecimal("3"), new BigDecimal("80"));
        Subscription d = new Subscription("Investor D", new BigDecimal("8"), new BigDecimal("160"));

        OptimalResult result = optimizer.optimize(new BigDecimal("15"), List.of(a, b, c, d));

        assertThat(result.finalSubscriptions()).containsExactlyInAnyOrder(a, b);
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("15");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("320");
    }

    @Test
    void fractionalAmounts_sumExactlyToCapacity_bothAccepted() {
        Subscription a = new Subscription("A", new BigDecimal("5.25"), new BigDecimal("120.10"));
        Subscription b = new Subscription("B", new BigDecimal("5.25"), new BigDecimal("130.90"));

        OptimalResult result = optimizer.optimize(new BigDecimal("10.50"), List.of(a, b));

        assertThat(result.finalSubscriptions()).containsExactlyInAnyOrder(a, b);
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("10.50");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("251.00");
    }

    @Test
    void precisionIsRespected_uptoFourthDecimalPlace() {
        Subscription a = new Subscription("A", new BigDecimal("5.0000"), new BigDecimal("90"));
        Subscription b = new Subscription("B", new BigDecimal("5.0002"), new BigDecimal("95"));

        OptimalResult result = optimizer.optimize(new BigDecimal("10.0001"), List.of(a, b));

        assertThat(result.finalSubscriptions()).containsExactly(b);
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("5.0002");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("95");
    }

    @Test
    void fifthDecimalPlace_throwsIllegalArgumentException() {
        Subscription a = new Subscription("A", new BigDecimal("5.00001"), new BigDecimal("90"));

        assertThatThrownBy(() -> optimizer.optimize(new BigDecimal("10"), List.of(a)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestedAmount");
    }

    @Test
    void largeFundCapacity_optimizesCorrectlyAndQuickly() {
        Subscription a = new Subscription("A", new BigDecimal("1200000.0000"), new BigDecimal("36000.0000"));
        Subscription b = new Subscription("B", new BigDecimal("800000.0000"), new BigDecimal("28000.0000"));
        Subscription c = new Subscription("C", new BigDecimal("2500000.0000"), new BigDecimal("55000.0000"));
        Subscription d = new Subscription("D", new BigDecimal("900000.0000"), new BigDecimal("40000.0000"));
        Subscription e = new Subscription("E", new BigDecimal("600000.0000"), new BigDecimal("21000.0000"));

        OptimalResult result = assertTimeout(Duration.ofMillis(500), () ->
                optimizer.optimize(new BigDecimal("5000000.0000"), List.of(a, b, c, d, e)));

        assert result != null;
        assertThat(result.finalSubscriptions()).containsExactlyInAnyOrder(b, c, d, e);
        assertThat(result.totalRequestedAmount()).isEqualByComparingTo("4800000.0000");
        assertThat(result.totalFeeRevenue()).isEqualByComparingTo("144000.0000");
    }

    @Test
    void negativeCapacity_throwsIllegalArgumentException() {
        // request validation should reject this before it gets to the optimizer, just in case.
        assertThatThrownBy(() -> optimizer.optimize(new BigDecimal("-1"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
