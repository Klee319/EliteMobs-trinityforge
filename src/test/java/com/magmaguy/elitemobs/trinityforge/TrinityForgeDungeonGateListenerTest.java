package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the REAL {@link TrinityForgeDungeonGateListener} entry points (not just the
 * {@link TrinityForgeIntegration} getters) to pin the {@code dungeon-entry-gate} emergency-stop switch
 * (HIGH-2, restored 2026-08-01).
 * <p>
 * Before this switch existed again, {@code TrinityForgeIntegration.isDungeonEntryGateEnabled()} did not
 * exist and there was no way to make {@link TrinityForgeDungeonGateListener} skip its TrinityForge gate
 * lookup short of removing TrinityForge itself. These tests set {@code available=true} +
 * {@code dungeonEntryGate=false} (the "TrinityForge is up but the admin hit the emergency stop" state)
 * and assert every entry point passes players through WITHOUT ever needing to reach
 * {@code TrinityForge.getInstance()} — which is what makes this test runnable at all without a live
 * TrinityForge plugin: the master-switch check must short-circuit before {@code resolveGateService()}.
 */
class TrinityForgeDungeonGateListenerTest {

    @AfterEach
    void reset() {
        IntegrationState.reset();
    }

    @Test
    @DisplayName("dungeon-entry-gate: false makes checkDungeonEntryAllowed pass every player through")
    void masterSwitchOff_checkDungeonEntryAllowed_passesThrough() {
        IntegrationState.set("available", true);
        IntegrationState.set("dungeonEntryGate", false);
        List<String> messages = new ArrayList<>();
        Player player = FakePlayer.create(Set.of(), messages);

        boolean allowed = TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, "some_dungeon");

        assertTrue(allowed, "the emergency stop must let an unprivileged, ungated player through");
        assertTrue(messages.isEmpty(),
                "an intentional admin OFF switch is not a failure state and must not warn the player");
    }

    @Test
    @DisplayName("dungeon-entry-gate: false makes previewDungeonEntryAllowed pass every player through")
    void masterSwitchOff_previewDungeonEntryAllowed_passesThrough() {
        IntegrationState.set("available", true);
        IntegrationState.set("dungeonEntryGate", false);
        Player player = FakePlayer.create(Set.of(), null);

        assertTrue(TrinityForgeDungeonGateListener.previewDungeonEntryAllowed(player, "some_dungeon"),
                "the dungeon-browser preflight must agree with the join check while the gate is off");
    }

    @Test
    @DisplayName("dungeon-entry-gate: false passes through even with no lookup key at all")
    void masterSwitchOff_blankLookupKey_stillPassesThrough() {
        IntegrationState.set("available", true);
        IntegrationState.set("dungeonEntryGate", false);
        Player player = FakePlayer.create(Set.of(), null);

        // With the gate ON, a null/blank lookup key is rejected (see the HIGH-3 tests). With the gate
        // OFF the lookup must never even be inspected — the switch disables gate evaluation outright.
        assertTrue(TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, null));
        assertTrue(TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, ""));
    }

    // Behaviour of the gate WHILE ON (dungeonEntryGate=true) — including the fail-open/fail-close split
    // for a missing lookup key, a null gate service and a thrown exception — is pinned in the HIGH-3
    // tests further down this file (added in a separate commit).
}
