package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the REAL {@link TrinityForgeDungeonGateListener} entry points (not just the
 * {@link TrinityForgeIntegration} getters) to pin two things:
 * <ul>
 *   <li>HIGH-2 (restored 2026-08-01): the {@code dungeon-entry-gate} emergency-stop switch. Before it
 *   existed again, {@code TrinityForgeIntegration.isDungeonEntryGateEnabled()} did not exist and there
 *   was no way to make the listener skip its TrinityForge gate lookup short of removing TrinityForge
 *   itself.</li>
 *   <li>HIGH-3 (2026-08-01): the fail-open/fail-close split documented on
 *   {@link TrinityForgeDungeonGateListener}'s class javadoc. "TrinityForge could not be asked" (no
 *   lookup key, no service, or a thrown exception) must fail OPEN; "TrinityForge said no" must stay
 *   fail-CLOSE.</li>
 * </ul>
 * These tests set {@code available=true} and drive the listener WITHOUT ever needing a live TrinityForge
 * plugin: with no real TrinityForge enabled in this JVM, {@code TrinityForge.getInstance()} returns
 * {@code null} (its backing static field is only ever set from a real {@code onEnable}), which is
 * exactly the "service unavailable" failure this class is supposed to fail open on — so it is naturally,
 * not artificially, reachable from a plain unit test.
 */
class TrinityForgeDungeonGateListenerTest {

    @BeforeEach
    void installServer() {
        // Logger.warn (MagmaCore) writes through Bukkit.getLogger() AND
        // MagmaCore.getInstance().getRequestingPlugin().getName() — several of the paths under test call
        // it (missing key, null service, thrown exception), so both fakes are needed or it NPEs.
        FakeBukkitServer.install();
        FakeMagmaCore.install();
    }

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

        // With the gate ON a null/blank lookup key is ALSO fail-open (see the HIGH-3 tests below) — the
        // point of this test is narrower: with the gate OFF the lookup must never even be inspected,
        // because the switch disables gate evaluation outright and must not be conflated with the
        // "couldn't determine the lookup key" failure mode.
        assertTrue(TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, null));
        assertTrue(TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, ""));
    }

    // -------------------------------------------------------------------------------------------------
    // HIGH-3: fail-open vs fail-close, with the gate switch ON (the normal, non-emergency-stop state).
    // -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("gate ON + no lookup key: fails open (was fail-close before HIGH-3)")
    void gateOn_blankLookupKey_failsOpen() {
        IntegrationState.set("available", true);
        IntegrationState.set("dungeonEntryGate", true);
        Player player = FakePlayer.create(Set.of(), null);

        assertTrue(TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, null),
                "a null lookup key means TrinityForge could not be asked at all — must fail open");
        assertTrue(TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, ""),
                "a blank lookup key means TrinityForge could not be asked at all — must fail open");
    }

    @Test
    @DisplayName("gate ON + gate service unavailable (no live TrinityForge): fails open")
    void gateOn_serviceUnavailable_failsOpen() {
        IntegrationState.set("available", true);
        IntegrationState.set("dungeonEntryGate", true);
        Player player = FakePlayer.create(Set.of(), null);

        // No real TrinityForge plugin is enabled in this JVM, so TrinityForge.getInstance() returns
        // null and resolveGateService() therefore returns null too — the exact "service unavailable"
        // condition this test is pinning, reached through the real production code path.
        assertTrue(TrinityForgeDungeonGateListener.checkDungeonEntryAllowed(player, "some_dungeon"),
                "an unreachable gate service must fail open, not lock every player out");
        assertTrue(TrinityForgeDungeonGateListener.previewDungeonEntryAllowed(player, "some_dungeon"),
                "the preview path must fail open the same way as the consuming join check");
    }

    @Test
    @DisplayName("evaluateOrFailOpen trusts a real TrinityForge decision as-is (fail-close preserved)")
    void evaluateOrFailOpen_trustsRealDecision() {
        Player player = FakePlayer.create(Set.of(), null);

        assertFalse(TrinityForgeDungeonGateListener.evaluateOrFailOpen(player, "ctx", () -> false),
                "TrinityForge saying \"no\" (under level / missing key) must stay a denial");
        assertTrue(TrinityForgeDungeonGateListener.evaluateOrFailOpen(player, "ctx", () -> true),
                "TrinityForge saying \"yes\" must stay an allow");
    }

    @Test
    @DisplayName("evaluateOrFailOpen fails open when the TrinityForge call itself throws")
    void evaluateOrFailOpen_exceptionFailsOpen() {
        Player player = FakePlayer.create(Set.of(), null);

        boolean afterRuntimeException = TrinityForgeDungeonGateListener.evaluateOrFailOpen(player, "ctx", () -> {
            throw new IllegalStateException("simulated TrinityForge API drift");
        });
        boolean afterLinkageError = TrinityForgeDungeonGateListener.evaluateOrFailOpen(player, "ctx", () -> {
            throw new NoSuchMethodError("simulated binary-incompatible TrinityForge jar");
        });

        assertTrue(afterRuntimeException,
                "a RuntimeException from the TrinityForge call means the query itself failed, not a real "
                        + "denial — must fail open, not lock the player out");
        assertTrue(afterLinkageError,
                "a LinkageError (mismatched TrinityForge jar) must also fail open, same as any other "
                        + "unreachable-service failure");
    }
}
