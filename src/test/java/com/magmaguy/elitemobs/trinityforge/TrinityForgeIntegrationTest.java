package com.magmaguy.elitemobs.trinityforge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TrinityForgeIntegration} availability gating.
 * <p>
 * The integration is bound to a live TrinityForge plugin at runtime, which is unavailable in a headless unit
 * test. Instead these tests drive the private static state ({@code available} + the per-feature toggles) via
 * reflection to verify the gating contract: every accessor is AND-ed with {@code available}, so when the
 * integration is unavailable the fork behaves like vanilla EliteMobs. State is restored after each test.
 */
class TrinityForgeIntegrationTest {

    @AfterEach
    void resetAvailability() throws Exception {
        setStaticBoolean("available", false);
    }

    @Test
    @DisplayName("when unavailable every feature getter reports disabled")
    void unavailable_allFeatureGettersDisabled() throws Exception {
        setStaticBoolean("available", false);
        // Turn the underlying toggles ON to prove they are gated purely by availability.
        for (String toggle : new String[]{
                "gearNeutralization", "combatDelegation", "combatLevelMapping", "spawnProfileStamp",
                "lootStatStamp", "hateTargeting", "repairDisabled",
                "soulbindBridge", "useLevelRestriction"}) {
            setStaticBoolean(toggle, true);
        }

        assertFalse(TrinityForgeIntegration.isAvailable());
        assertFalse(TrinityForgeIntegration.isGearNeutralizationEnabled());
        assertFalse(TrinityForgeIntegration.isCombatDelegationEnabled());
        assertFalse(TrinityForgeIntegration.isCombatLevelMappingEnabled());
        assertFalse(TrinityForgeIntegration.isSpawnProfileStampEnabled());
        assertFalse(TrinityForgeIntegration.isLootStatStampEnabled());
        assertFalse(TrinityForgeIntegration.isHateTargetingEnabled());
        assertFalse(TrinityForgeIntegration.isRepairDisabled());
        assertFalse(TrinityForgeIntegration.isSoulbindBridgeEnabled());
        assertFalse(TrinityForgeIntegration.isUseLevelRestrictionEnabled());
    }

    @Test
    @DisplayName("when available a feature getter follows its own toggle")
    void available_featureGetterFollowsToggle() throws Exception {
        setStaticBoolean("available", true);

        setStaticBoolean("gearNeutralization", true);
        assertTrue(TrinityForgeIntegration.isGearNeutralizationEnabled());

        setStaticBoolean("gearNeutralization", false);
        assertFalse(TrinityForgeIntegration.isGearNeutralizationEnabled());

        setStaticBoolean("hateTargeting", true);
        assertTrue(TrinityForgeIntegration.isHateTargetingEnabled());
        setStaticBoolean("hateTargeting", false);
        assertFalse(TrinityForgeIntegration.isHateTargetingEnabled());
    }

    @Test
    @DisplayName("shutdown marks the integration unavailable and clears service handles")
    void shutdown_marksUnavailable() throws Exception {
        setStaticBoolean("available", true);
        assertTrue(TrinityForgeIntegration.isAvailable());

        TrinityForgeIntegration.shutdown();

        assertFalse(TrinityForgeIntegration.isAvailable());
        assertEquals(null, TrinityForgeIntegration.combatService());
        assertEquals(null, TrinityForgeIntegration.config());
        assertEquals(null, TrinityForgeIntegration.itemFactory());
    }

    private static void setStaticBoolean(String fieldName, boolean value) throws Exception {
        Field field = TrinityForgeIntegration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }
}
