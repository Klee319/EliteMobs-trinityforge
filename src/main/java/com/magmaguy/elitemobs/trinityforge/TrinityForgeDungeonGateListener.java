package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.api.PlayerPreTeleportEvent;
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
 */
public class TrinityForgeDungeonGateListener implements Listener {
    private static final String ADMIN_PERMISSION = "trinityforge.admin";
    private static final String TOOLING_PERMISSION = "trinityforge.elitemobs.commands";
    private static final String GATE_UNAVAILABLE_MESSAGE =
            "§cダンジョン入場条件を確認できないため入場できません。管理者へ連絡してください。";
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
            player.sendMessage(GATE_UNAVAILABLE_MESSAGE);
            return false;
        }
        DungeonGateService service = resolveGateService();
        if (service == null) {
            player.sendMessage(GATE_UNAVAILABLE_MESSAGE);
            return false;
        }
        try {
            return consume
                    ? service.checkRequiredEntry(player, lookupKey)
                    : service.previewRequiredEntry(player, lookupKey);
        } catch (RuntimeException | LinkageError e) {
            player.sendMessage(GATE_UNAVAILABLE_MESSAGE);
            return false;
        }
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
            return true;
        }
        try {
            return service.checkEntry(player, lookupKey);
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
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
}
