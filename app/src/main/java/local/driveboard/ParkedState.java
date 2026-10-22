package local.driveboard;

/** Pure lock policy. All timestamps use the same monotonic millisecond clock. */
public final class ParkedState {
    public enum State { MOVING, PARKED, UNKNOWN }

    private ParkedState() {}

    public static State evaluate(boolean permission, long now, long fixAt,
            boolean hasSpeed, double speedKmh, long stationarySince) {
        if (!permission || fixAt < 0 || fixAt > now || now - fixAt > 120_000) {
            return State.UNKNOWN;
        }
        if (hasSpeed) {
            if (!Double.isFinite(speedKmh) || speedKmh < 0) {
                return State.UNKNOWN;
            }
            if (speedKmh > 8) {
                return now - fixAt <= 10_000 ? State.MOVING : State.UNKNOWN;
            }
            return State.PARKED;
        }
        // The stationary interval must be observed by fixes, not merely by a timer.
        return stationarySince >= 0 && stationarySince <= fixAt
                && fixAt - stationarySince >= 10_000 ? State.PARKED : State.UNKNOWN;
    }
}
