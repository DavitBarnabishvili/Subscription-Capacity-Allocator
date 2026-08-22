package com.arcticblu.subscriptioncapacityallocator.algorithm;

import com.arcticblu.subscriptioncapacityallocator.util.DecimalScaler;

import java.math.BigDecimal;
import java.util.*;

public class Optimizer {

    // more precise than necessary in the real world, but doesn't cost much to keep, and it's what the db can handle.
    private static final int SCALING_FACTOR = 4;

    // takenIndex tells us which subs inclusion produced this state, -1 for the start
    private record State(long revenue, Optimizer.State parent, int takenIndex) { }

    public OptimalResult optimize(BigDecimal maxCapacity, List<Subscription> candidates) {

        if (maxCapacity.signum() < 0)  throw new IllegalArgumentException("maxCapacity cannot be negative");

        long capacity = safeScaleToLong(maxCapacity, "maxCapacity");

        Map<Long, State> reachableStates = new HashMap<>();
        reachableStates.put(0L, new State(0L, null, -1));

        for (int i = 0; i < candidates.size(); i++) {
            Subscription s = candidates.get(i);
            long amount = safeScaleToLong(s.requestedAmount(), "requestedAmount");
            long revenue = safeScaleToLong(s.feeRevenue(), "feeRevenue");

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
            totalRequestedAmount += safeScaleToLong(chosenSub.requestedAmount(), "requestedAmount");
        }

        return new OptimalResult(
                acceptedSubs,
                DecimalScaler.toBigDecimal(totalRequestedAmount, SCALING_FACTOR),
                DecimalScaler.toBigDecimal(best.revenue, SCALING_FACTOR));
    }

    // just for safety and clarity of error messages
    private static long safeScaleToLong(BigDecimal value, String fieldName) {
        try {
            return DecimalScaler.toLong(value, SCALING_FACTOR);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    fieldName + " can not have more than " + SCALING_FACTOR +
                    " decimal places or exceed the range of a long when scaled", e);
        }
    }
}