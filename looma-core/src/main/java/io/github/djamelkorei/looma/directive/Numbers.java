package io.github.djamelkorei.looma.directive;

/** Small arithmetic helpers for loop counters. */
final class Numbers {

    private Numbers() {}

    static boolean integral(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }

    static Number negate(Number n) {
        return integral(n) ? (Number) (-n.longValue()) : (Number) (-n.doubleValue());
    }

    /** Integer-preserving addition: int + int stays an Integer so comparisons and output look natural. */
    static Number add(Number a, Number b) {
        if (integral(a) && integral(b)) {
            long sum = Math.addExact(a.longValue(), b.longValue());
            return sum >= Integer.MIN_VALUE && sum <= Integer.MAX_VALUE ? (Number) (int) sum : (Number) sum;
        }
        return a.doubleValue() + b.doubleValue();
    }
}
