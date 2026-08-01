package com.magmaguy.elitemobs.trinityforge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for {@link NativeDisplayPolicy} — the decisions the production nametag paths run.
 * See {@link EliteEntityNametagTest} for the end-to-end proof that {@code EliteEntity#setNameVisible}
 * actually writes {@code false} onto the entity.
 */
class NativeDisplayPolicyTest {

    @AfterEach
    void reset() {
        IntegrationState.reset();
    }

    @Test
    @DisplayName("suppression can only hide, never force-show")
    void suppressionOnlyHides() {
        IntegrationState.set("available", true);

        IntegrationState.set("suppressNametag", true);
        assertFalse(NativeDisplayPolicy.resolveNametagVisible(true),
                "this is the EliteMobEnterCombatEvent re-show that the fix exists for");
        assertFalse(NativeDisplayPolicy.resolveNametagVisible(false));

        IntegrationState.set("suppressNametag", false);
        assertTrue(NativeDisplayPolicy.resolveNametagVisible(true));
        assertFalse(NativeDisplayPolicy.resolveNametagVisible(false), "a hide request is never turned into a show");
    }

    @Test
    @DisplayName("nametag suppression is inert when TrinityForge is absent")
    void standaloneKeepsNativeDisplays() {
        IntegrationState.set("available", false);
        IntegrationState.set("suppressNametag", true);
        IntegrationState.set("suppressCustomModelNametag", true);
        IntegrationState.set("suppressBossTrackingBar", true);

        assertTrue(NativeDisplayPolicy.resolveNametagVisible(true));
        assertTrue(NativeDisplayPolicy.resolveCustomBossNametagVisible(false, true));
        assertTrue(NativeDisplayPolicy.resolveCustomModelNametagVisible(true));
        assertTrue(NativeDisplayPolicy.resolveSpawnNametagVisible(true, false));
        assertTrue(NativeDisplayPolicy.allowBossTrackingBar());
    }

    @Test
    @DisplayName("a custom boss' alwaysShowName override is itself suppressed")
    void alwaysShowNameIsSuppressed() {
        IntegrationState.set("available", true);

        IntegrationState.set("suppressNametag", true);
        assertFalse(NativeDisplayPolicy.resolveCustomBossNametagVisible(false, true),
                "alwaysShowName is exactly why custom bosses kept their nametag");
        assertFalse(NativeDisplayPolicy.resolveCustomBossNametagVisible(true, true));

        IntegrationState.set("suppressNametag", false);
        assertTrue(NativeDisplayPolicy.resolveCustomBossNametagVisible(false, true));
        assertFalse(NativeDisplayPolicy.resolveCustomBossNametagVisible(false, false));
    }

    @Test
    @DisplayName("the custom-boss SPAWN path suppresses both alwaysShowNametags and alwaysShowName")
    void spawnPathIsSuppressed() {
        IntegrationState.set("available", true);

        IntegrationState.set("suppressNametag", true);
        assertFalse(NativeDisplayPolicy.resolveSpawnNametagVisible(true, false),
                "CustomBossMegaConsumer is the path every custom boss spawns through");
        assertFalse(NativeDisplayPolicy.resolveSpawnNametagVisible(false, true));

        IntegrationState.set("suppressNametag", false);
        assertTrue(NativeDisplayPolicy.resolveSpawnNametagVisible(true, false));
        assertTrue(NativeDisplayPolicy.resolveSpawnNametagVisible(false, true));
        assertFalse(NativeDisplayPolicy.resolveSpawnNametagVisible(false, false),
                "with neither flag set the nametag was never shown in the first place");
    }

    @Test
    @DisplayName("vanilla nametag and custom-model nametag are independent switches")
    void nametagAndModelNametagAreIndependent() {
        IntegrationState.set("available", true);

        IntegrationState.set("suppressNametag", true);
        IntegrationState.set("suppressCustomModelNametag", false);
        assertFalse(NativeDisplayPolicy.resolveNametagVisible(true));
        assertTrue(NativeDisplayPolicy.resolveCustomModelNametagVisible(true));

        IntegrationState.set("suppressNametag", false);
        IntegrationState.set("suppressCustomModelNametag", true);
        assertTrue(NativeDisplayPolicy.resolveNametagVisible(true));
        assertFalse(NativeDisplayPolicy.resolveCustomModelNametagVisible(true));
    }

    @Test
    @DisplayName("the boss tracking bar is allowed unless explicitly suppressed")
    void bossTrackingBar() {
        IntegrationState.set("available", true);

        IntegrationState.set("suppressBossTrackingBar", false);
        assertTrue(NativeDisplayPolicy.allowBossTrackingBar(),
                "it shows distance/direction, which TrinityForge has no equivalent for");

        IntegrationState.set("suppressBossTrackingBar", true);
        assertFalse(NativeDisplayPolicy.allowBossTrackingBar());
    }
}
