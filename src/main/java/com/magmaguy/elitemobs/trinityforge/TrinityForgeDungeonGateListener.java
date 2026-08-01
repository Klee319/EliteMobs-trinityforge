package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.api.PlayerPreTeleportEvent;
import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.TrinityForge;
import com.trinityforge.mobs.DungeonGateService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Delegates dungeon entry gating to TrinityForge {@link DungeonGateService} ({@code dungeon/gates.yml}).
 * EliteMobs instanced-dungeon creation/join and cross-world teleports share one config.
 * <p>
 * <b>Fail-open vs fail-close (HIGH-3, 2026-08-01).</b> This class makes two DIFFERENT kinds of
 * "no" on purpose, and they must not be confused:
 * <ul>
 *   <li><b>TrinityForge was reachable and said "no"</b> (a real gate exists and the player is under
 *   level / missing the key item) — the {@code DungeonGateService} return value is trusted as-is
 *   (fail-CLOSE). This is TrinityForge's own gate, doing its job; {@code DungeonGateService} sends its
 *   own denial message to the player.</li>
 *   <li><b>TrinityForge could not be asked at all</b> — no lookup key was resolved, the gate service
 *   handle is {@code null} (an initialization-order or partial-startup problem), or the call itself threw
 *   a {@link RuntimeException}/{@link LinkageError} (an API drift between this fork and the installed
 *   TrinityForge jar) — entry is allowed (fail-OPEN) and a warning is logged. A plugin wiring accident on
 *   TrinityForge's side must never lock ordinary players out of every dungeon; that is what the
 *   {@code dungeon-entry-gate} emergency-stop switch (HIGH-2, see
 *   {@link TrinityForgeIntegration#isDungeonEntryGateEnabled()}) is a deliberate, admin-controlled
 *   version of. This is the same idea applied to the UNINTENTIONAL failure modes.</li>
 * </ul>
 */
public class TrinityForgeDungeonGateListener implements Listener {
    private static final String ADMIN_PERMISSION = "trinityforge.admin";
    private static final String TOOLING_PERMISSION = "trinityforge.elitemobs.commands";
    // 「ゲート未設定」の文面は TrinityForge 側 (DungeonGateService#UNCONFIGURED_GATE) が出す。
    // EliteMobs 側で同じ判定を持つと「ゲート0本なら機能ごと無効」の逃げ道を取りこぼすため、
    // ここでは hasEntryGate を呼ばない。詳細は DungeonCommands#teleport のコメント。

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreTeleport(PlayerPreTeleportEvent event) {
        Location destination = event.getDestination();
        Player player = event.getPlayer();
        if (destination == null || destination.getWorld() == null || player == null) {
            return;
        }
        if (!checkConfiguredTeleportAllowed(player, destination.getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param lookupKey destination world name or EliteMobs content-package filename
     * @return {@code true} if entry is allowed
     */
    public static boolean checkDungeonEntryAllowed(Player player, String lookupKey) {
        return checkDungeonEntry(player, lookupKey, true);
    }

    /**
     * Performs the same required-gate evaluation before an expensive instance clone, but leaves the
     * key untouched. The later participant join calls {@link #checkDungeonEntryAllowed} and consumes it.
     */
    public static boolean previewDungeonEntryAllowed(Player player, String lookupKey) {
        return checkDungeonEntry(player, lookupKey, false);
    }

    private static boolean checkDungeonEntry(Player player, String lookupKey, boolean consume) {
        if (player == null) {
            return false;
        }
        if (!TrinityForgeIntegration.isAvailable()) {
            return true;
        }
        if (!TrinityForgeIntegration.isDungeonEntryGateEnabled()) {
            // Emergency stop: trinityforge.yml `dungeon-entry-gate: false`. Skip the gate lookup entirely
            // instead of treating it as an unreachable-service failure — this is an intentional admin
            // choice, not a degraded state, so it must not log a warning like the fail-open paths below.
            return true;
        }
        if (player.hasPermission(ADMIN_PERMISSION) || player.hasPermission(TOOLING_PERMISSION)) {
            return true;
        }
        if (lookupKey == null || lookupKey.isBlank()) {
            // Could not even determine WHAT to ask TrinityForge about — a "TrinityForge unreachable"
            // failure, not a "TrinityForge said no" decision. Fail OPEN (see class javadoc).
            Logger.warn("TrinityForge dungeon gate: no lookup key resolved for " + player.getName()
                    + " — allowing entry (fail-open, see TrinityForgeDungeonGateListener javadoc).");
            return true;
        }
        DungeonGateService service = resolveGateService();
        if (service == null) {
            Logger.warn("TrinityForge dungeon gate service unavailable for " + player.getName()
                    + " (lookupKey=" + lookupKey
                    + ") — allowing entry (fail-open, see TrinityForgeDungeonGateListener javadoc).");
            return true;
        }
        return evaluateOrFailOpen(player, "lookupKey=" + lookupKey, () -> consume
                ? service.checkRequiredEntry(player, lookupKey)
                : service.previewRequiredEntry(player, lookupKey));
    }

    /**
     * Shared EliteMobs teleport hook. Unlike an explicit dungeon join, unrelated routes such as
     * {@code /em spawntp}, the Adventurers Guild and NPC return teleports are allowed when their
     * destination has no dungeon gate.
     */
    private static boolean checkConfiguredTeleportAllowed(Player player, String lookupKey) {
        if (!TrinityForgeIntegration.isAvailable()
                || !TrinityForgeIntegration.isDungeonEntryGateEnabled()
                || player.hasPermission(ADMIN_PERMISSION)
                || player.hasPermission(TOOLING_PERMISSION)) {
            return true;
        }
        DungeonGateService service = resolveGateService();
        if (service == null) {
            Logger.warn("TrinityForge dungeon gate service unavailable for " + player.getName()
                    + " (teleport lookupKey=" + lookupKey
                    + ") — allowing teleport (fail-open, see TrinityForgeDungeonGateListener javadoc).");
            return true;
        }
        return evaluateOrFailOpen(player, "teleport lookupKey=" + lookupKey,
                () -> service.checkEntry(player, lookupKey));
    }

    private static DungeonGateService resolveGateService() {
        if (!TrinityForgeIntegration.isAvailable()) {
            return null;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            return tf == null ? null : tf.dungeonGateService();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Runs a resolved {@code DungeonGateService} query and trusts its boolean result as-is (fail-CLOSE:
     * "TrinityForge said no" stays no — see class javadoc). A thrown {@link RuntimeException} or
     * {@link LinkageError} means the query itself could not complete (API drift between this fork and
     * the installed TrinityForge jar, or a bug on TrinityForge's side) rather than a real decision, so
     * that case fails OPEN with a warning log instead.
     * <p>
     * Package-private, and takes the query as a {@link GateOperation} rather than calling the service
     * directly, purely so a unit test can inject a throwing operation: {@code DungeonGateService} is a
     * {@code final} class with no accessible test seam, so there is no other way to drive this catch
     * block without a live TrinityForge instance.
     */
    static boolean evaluateOrFailOpen(Player player, String context, GateOperation operation) {
        try {
            return operation.evaluate();
        } catch (RuntimeException | LinkageError e) {
            Logger.warn("TrinityForge dungeon gate check threw for " + player.getName()
                    + " (" + context + "): " + e
                    + " — allowing entry (fail-open, see TrinityForgeDungeonGateListener javadoc).");
            return true;
        }
    }

    /** A TrinityForge gate query that may throw if the installed jar's API has drifted from this fork's. */
    @FunctionalInterface
    interface GateOperation {
        boolean evaluate();
    }
}
