package com.magmaguy.elitemobs.trinityforge;

import com.trinityforge.pdc.ItemData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the deterministic uniform-quality fallback.
 * <p>
 * {@code uniformQuality} is a pure, seed-deterministic helper (no Bukkit dependency), so it is headless-testable.
 * It is private, so it is invoked through reflection. {@code ItemData.MIN_QUALITY}/{@code MAX_QUALITY} are
 * compile-time {@code static final int} constants, so referencing them here does not load any Bukkit-bound
 * static initializer.
 */
class TrinityForgeLootListenerTest {

    private static int rollQuality(long seed) throws Exception {
        Method method = TrinityForgeLootListener.class.getDeclaredMethod(
                "uniformQuality", long.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, seed, ItemData.MAX_QUALITY);
    }

    @Test
    @DisplayName("same seed yields the same quality (deterministic)")
    void rollQuality_sameSeed_isDeterministic() throws Exception {
        long seed = 123456789L;
        assertEquals(rollQuality(seed), rollQuality(seed));
        assertEquals(rollQuality(-42L), rollQuality(-42L));
    }

    @Test
    @DisplayName("quality stays within [MIN_QUALITY, MAX_QUALITY] across many seeds")
    void rollQuality_staysWithinBounds() throws Exception {
        for (long seed = -5000; seed <= 5000; seed += 7) {
            int quality = rollQuality(seed);
            assertTrue(quality >= ItemData.MIN_QUALITY && quality <= ItemData.MAX_QUALITY,
                    "quality " + quality + " out of bounds for seed " + seed);
        }
    }

    @Test
    @DisplayName("seeding semantics match SplittableRandom over the quality span")
    void rollQuality_matchesSplittableRandomSeeding() throws Exception {
        int span = ItemData.MAX_QUALITY - ItemData.MIN_QUALITY + 1;
        for (long seed : new long[]{0L, 1L, 99L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            int expected = ItemData.MIN_QUALITY + new SplittableRandom(seed).nextInt(span);
            assertEquals(expected, rollQuality(seed), "seeding mismatch for seed " + seed);
        }
    }
}
