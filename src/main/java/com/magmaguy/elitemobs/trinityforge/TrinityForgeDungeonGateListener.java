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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreTeleport(PlayerPreTeleportEvent event) {
        Location destination = event.getDestination();
        Player player = event.getPlayer();
        if (destination == null || destination.getWorld() == null || player == null) {
            return;
        }
        if (!checkDungeonEntryAllowed(player, destination.getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param lookupKey destination world name or EliteMobs content-package filename
     * @return {@code true} if entry is allowed
     */
    public static boolean checkDungeonEntryAllowed(Player player, String lookupKey) {
        if (player == null || lookupKey == null || lookupKey.isBlank()) {
            return true;
        }
        if (!TrinityForgeIntegration.isDungeonEntryGateEnabled()) {
            return true;
        }
        DungeonGateService service = resolveGateService();
        if (service == null) {
            // Fail open when TF gate service unavailable
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
