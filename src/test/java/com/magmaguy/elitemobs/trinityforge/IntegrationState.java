package com.magmaguy.elitemobs.trinityforge;

import java.lang.reflect.Field;

/**
 * Test-only accessor for {@link TrinityForgeIntegration}'s private static toggles.
 * <p>
 * The real values are written by a Bukkit config load, which the unit tests cannot perform, so the
 * tests drive the same fields directly. Kept in one place so every test in this package agrees on the
 * field names and on what "reset" means.
 */
final class IntegrationState {

    /** Every {@code elite-drop-sources} toggle field, in trinityforge.yml order. */
    static final String[] DROP_TOGGLES = {
            "allowRandomEliteLoot", "allowSpecialLoot", "allowEliteScroll",
            "allowVanillaLootMultiplier", "allowVanillaLoot", "allowCurrencyShower", "allowBossUniqueLoot"};

    /** Every {@code native-display-suppression} toggle field, in trinityforge.yml order. */
    static final String[] DISPLAY_TOGGLES = {
            "suppressNametag", "suppressCustomModelNametag", "suppressBossTrackingBar"};

    private IntegrationState() {
    }

    static void set(String fieldName, boolean value) {
        try {
            Field field = TrinityForgeIntegration.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("TrinityForgeIntegration." + fieldName
                    + " no longer exists — the test that drives it must be updated, not deleted.", e);
        }
    }

    static void setAll(String[] fieldNames, boolean value) {
        for (String fieldName : fieldNames) set(fieldName, value);
    }

    /**
     * Restores the "TrinityForge absent" baseline. Called from {@code @AfterEach} everywhere so one
     * test's static writes cannot leak into the next.
     */
    static void reset() {
        set("available", false);
        setAll(DROP_TOGGLES, true);
        setAll(DISPLAY_TOGGLES, false);
        set("dungeonEntryGate", true);
    }
}
