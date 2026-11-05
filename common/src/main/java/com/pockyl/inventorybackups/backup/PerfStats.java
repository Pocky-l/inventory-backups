package com.pockyl.inventorybackups.backup;

/**
 * Main-thread cost of change tracking, for {@code /inventory stats} and the performance test. Only touched on the server
 * thread, so plain fields suffice.
 */
public final class PerfStats {
    private static long trackingNanos;
    private static long playerTicks;
    private static long captures;
    private static long captureNanos;

    private PerfStats() {
    }

    static void addTick(long nanos, int players) {
        trackingNanos += nanos;
        playerTicks += players;
    }

    static void addEvent(long nanos) {
        trackingNanos += nanos;
    }

    static void addCapture(long nanos) {
        captures++;
        captureNanos += nanos;
    }

    public static void reset() {
        trackingNanos = 0;
        playerTicks = 0;
        captures = 0;
        captureNanos = 0;
    }

    public static Snapshot snapshot() {
        return new Snapshot(trackingNanos, playerTicks, captures, captureNanos);
    }

    /**
     * @param nanos       time spent in ticks, listeners and events (captures included)
     * @param playerTicks sum over ticks of the number of tracked players
     */
    public record Snapshot(long nanos, long playerTicks, long captures, long captureNanos) {
        /** Average main-thread milliseconds per player and tick. */
        public double millisPerPlayerTick() {
            return playerTicks == 0 ? 0 : nanos / 1_000_000.0 / playerTicks;
        }

        public double millisPerCapture() {
            return captures == 0 ? 0 : captureNanos / 1_000_000.0 / captures;
        }
    }
}
