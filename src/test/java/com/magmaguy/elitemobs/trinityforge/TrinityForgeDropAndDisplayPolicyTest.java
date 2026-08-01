package com.magmaguy.elitemobs.trinityforge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the 2026-08-01 additions to {@link TrinityForgeIntegration}: the per-source
 * EliteMobs drop gates ({@code elite-drop-sources}) and the granular native-display suppression
 * ({@code native-display-suppression}).
 * <p>
 * Two gating directions have to hold and they are opposites, which is the whole point of these tests:
 * <ul>
 *   <li><b>Display suppression fails CLOSED on availability</b> — {@code available == false} means
 *       TrinityForge is not drawing anything, so EliteMobs must keep every native display.</li>
 *   <li><b>Drop gates fail OPEN on availability</b> — {@code available == false} means a standalone
 *       EliteMobs install, which must drop exactly what upstream drops.</li>
 * </ul>
 * As in {@code TrinityForgeIntegrationTest}, the private static state is driven by reflection because
 * the real values come from a live Bukkit config load.
 */
class TrinityForgeDropAndDisplayPolicyTest {

    private static final String[] DROP_TOGGLES = {
            "allowRandomEliteLoot", "allowSpecialLoot", "allowEliteScroll",
            "allowVanillaLootMultiplier", "allowVanillaLoot", "allowCurrencyShower", "allowBossUniqueLoot"};

    private static final String[] DISPLAY_TOGGLES = {
            "suppressNametag", "suppressCustomModelNametag", "suppressBossTrackingBar"};

    @AfterEach
    void reset() throws Exception {
        setStaticBoolean("available", false);
    }

    @Test
    @DisplayName("drop gates fail OPEN when TrinityForge is unavailable (standalone EliteMobs is untouched)")
    void unavailable_everyDropSourceAllowed() throws Exception {
        setStaticBoolean("available", false);
        // Every underlying toggle blocked: availability alone must still allow all of them.
        for (String toggle : DROP_TOGGLES) setStaticBoolean(toggle, false);

        assertTrue(TrinityForgeIntegration.isRandomEliteLootAllowed());
        assertTrue(TrinityForgeIntegration.isSpecialLootAllowed());
        assertTrue(TrinityForgeIntegration.isEliteScrollAllowed());
        assertTrue(TrinityForgeIntegration.isVanillaLootMultiplierAllowed());
        assertTrue(TrinityForgeIntegration.isVanillaLootAllowed());
        assertTrue(TrinityForgeIntegration.isCurrencyShowerAllowed());
        assertTrue(TrinityForgeIntegration.isBossUniqueLootAllowed());
    }

    @Test
    @DisplayName("when available each drop gate follows its own toggle")
    void available_dropGatesFollowTheirToggles() throws Exception {
        setStaticBoolean("available", true);

        for (String toggle : DROP_TOGGLES) setStaticBoolean(toggle, false);
        assertFalse(TrinityForgeIntegration.isRandomEliteLootAllowed());
        assertFalse(TrinityForgeIntegration.isSpecialLootAllowed());
        assertFalse(TrinityForgeIntegration.isEliteScrollAllowed());
        assertFalse(TrinityForgeIntegration.isVanillaLootMultiplierAllowed());
        assertFalse(TrinityForgeIntegration.isVanillaLootAllowed());
        assertFalse(TrinityForgeIntegration.isCurrencyShowerAllowed());
        assertFalse(TrinityForgeIntegration.isBossUniqueLootAllowed());

        for (String toggle : DROP_TOGGLES) setStaticBoolean(toggle, true);
        assertTrue(TrinityForgeIntegration.isRandomEliteLootAllowed());
        assertTrue(TrinityForgeIntegration.isSpecialLootAllowed());
        assertTrue(TrinityForgeIntegration.isEliteScrollAllowed());
        assertTrue(TrinityForgeIntegration.isVanillaLootMultiplierAllowed());
        assertTrue(TrinityForgeIntegration.isVanillaLootAllowed());
        assertTrue(TrinityForgeIntegration.isCurrencyShowerAllowed());
        assertTrue(TrinityForgeIntegration.isBossUniqueLootAllowed());
    }

    @Test
    @DisplayName("display suppression fails CLOSED when TrinityForge is unavailable")
    void unavailable_noDisplayIsSuppressed() throws Exception {
        setStaticBoolean("available", false);
        for (String toggle : DISPLAY_TOGGLES) setStaticBoolean(toggle, true);

        assertFalse(TrinityForgeIntegration.isSuppressNametagEnabled());
        assertFalse(TrinityForgeIntegration.isSuppressCustomModelNametagEnabled());
        assertFalse(TrinityForgeIntegration.isSuppressBossTrackingBarEnabled());
    }

    @Test
    @DisplayName("when available each display suppression follows its own toggle")
    void available_displaySuppressionFollowsToggles() throws Exception {
        setStaticBoolean("available", true);

        for (String toggle : DISPLAY_TOGGLES) setStaticBoolean(toggle, true);
        assertTrue(TrinityForgeIntegration.isSuppressNametagEnabled());
        assertTrue(TrinityForgeIntegration.isSuppressCustomModelNametagEnabled());
        assertTrue(TrinityForgeIntegration.isSuppressBossTrackingBarEnabled());

        for (String toggle : DISPLAY_TOGGLES) setStaticBoolean(toggle, false);
        assertFalse(TrinityForgeIntegration.isSuppressNametagEnabled());
        assertFalse(TrinityForgeIntegration.isSuppressCustomModelNametagEnabled());
        assertFalse(TrinityForgeIntegration.isSuppressBossTrackingBarEnabled());
    }

    @Test
    @DisplayName("nametag suppression is independent of the custom-model nametag suppression")
    void nametagAndCustomModelNametagAreIndependent() throws Exception {
        setStaticBoolean("available", true);

        setStaticBoolean("suppressNametag", true);
        setStaticBoolean("suppressCustomModelNametag", false);
        assertTrue(TrinityForgeIntegration.isSuppressNametagEnabled());
        assertFalse(TrinityForgeIntegration.isSuppressCustomModelNametagEnabled());

        setStaticBoolean("suppressNametag", false);
        setStaticBoolean("suppressCustomModelNametag", true);
        assertFalse(TrinityForgeIntegration.isSuppressNametagEnabled());
        assertTrue(TrinityForgeIntegration.isSuppressCustomModelNametagEnabled());
    }

    private static void setStaticBoolean(String fieldName, boolean value) throws Exception {
        Field field = TrinityForgeIntegration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }
}
