package local.driveboard;

/** Bounds catalog discovery to fifteen consecutive misses after the last hit. */
public final class QariProbe {
    public static final int MAX_ID = 119;
    public static final int MISS_LIMIT = 15;

    private QariProbe() {}

    public static int lastIdToCheck(int highestHit) {
        return Math.min(MAX_ID, highestHit < 0 ? MISS_LIMIT - 1 : highestHit + MISS_LIMIT);
    }

    public static int batchEnd(int nextId, int highestHit, int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("batchSize");
        return Math.min(lastIdToCheck(highestHit), nextId + batchSize - 1);
    }

    public static boolean complete(int nextId, int highestHit) {
        return nextId > lastIdToCheck(highestHit);
    }
}
