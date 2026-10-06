package com.archiangel.quadhotbar.client;

final class QuadHotbarFade {

    private static final long FADE_DURATION_NANOS = 1_000_000_000L;

    private QuadHotbarFade() {}

    static int color(long untilNanos, int rgb) {
        if (untilNanos == 0) {
            return 0;
        }
        long remaining = untilNanos - System.nanoTime();
        if (remaining <= 0) {
            return 0;
        }
        int alpha =
                remaining >= FADE_DURATION_NANOS
                        ? 255
                        : Math.max(4, (int) (255 * remaining / FADE_DURATION_NANOS));
        return alpha << 24 | rgb & 0xFFFFFF;
    }
}
