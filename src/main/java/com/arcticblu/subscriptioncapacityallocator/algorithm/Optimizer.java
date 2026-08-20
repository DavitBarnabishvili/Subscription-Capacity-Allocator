package com.arcticblu.subscriptioncapacityallocator.algorithm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class Optimizer {

    // more precise than necessary in the real world, but doesn't cost much to keep, and it's what the db can handle.
    private static final int SCALING_FACTOR = 4;

    // takenIndex tells us which subs inclusion produced this state, -1 for the start
    private record State(long revenue, Optimizer.State parent, int takenIndex) { }

    public OptimalResult optimize(BigDecimal maxCapacity, List<Subscription> candidates) {

        if (maxCapacity.signum() < 0)  throw new IllegalArgumentException("maxCapacity cannot be negative");

        long capacity = scaleToLong(maxCapacity);

        Map<Long, State> reachableStates = new HashMap<>();
        reachableStates.put(0L, new State(0L, null, -1));

        for (int i = 0; i < candidates.size(); i++) {
            Subscription s = candidates.get(i);
            long amount = scaleToLong(s.requestedAmount());
            long revenue = scaleToLong(s.feeRevenue());

            Map<Long, State> nextStates = new HashMap<>(reachableStates);

            for (Map.Entry<Long, State> entry : reachableStates.entrySet()) {
                long newAmount = entry.getKey() + amount;

                if (newAmount > capacity) continue;

                State currentState = entry.getValue();
                long newRevenue = currentState.revenue + revenue;

                //checking to see if this weight has been reached
                // 1) if it hasn't been reached, we can add it.
                // 2) if it has been reached, we update the value if it's more than what was reached before.
                State exists = nextStates.get(newAmount);
                if (exists == null || newRevenue > exists.revenue) {
                    State newState = new State(newRevenue, currentState, i);
                    nextStates.put(newAmount, newState);
                }
            }
            reachableStates = nextStates;
        }

        State best = Collections.max(reachableStates.values(), Comparator.comparingLong(a -> a.revenue));

        return buildResult(best, candidates);
    }

    private OptimalResult buildResult(State best, List<Subscription> candidates) {
        List<Subscription> acceptedSubs = new ArrayList<>();
        long totalRequestedAmount = 0L;

        for (State current = best; current.parent != null; current = current.parent) {
            Subscription chosenSub = candidates.get(current.takenIndex);
            acceptedSubs.add(chosenSub);
            totalRequestedAmount += scaleToLong(chosenSub.requestedAmount());
        }

        return new OptimalResult(acceptedSubs, descaleToDecimal(totalRequestedAmount), descaleToDecimal(best.revenue));
    }

    // scaling to avoid floating point issues. Converting to long is just easier to work with in comparisons and arithmetic.
    private static long scaleToLong(BigDecimal value) {
        try {
            return value.setScale(SCALING_FACTOR, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException( "Issue converting " + value + " to long", e);
        }
    }

    private static BigDecimal descaleToDecimal(long value) {
        return BigDecimal.valueOf(value, SCALING_FACTOR);
    }
}