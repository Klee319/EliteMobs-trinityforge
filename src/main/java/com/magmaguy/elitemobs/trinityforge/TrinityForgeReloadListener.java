package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * MOB-08 fix: re-binds the TrinityForge integration when TrinityForge (re)enables on its own, without
 * EliteMobs itself restarting.
 * <p>
 * {@link TrinityForgeIntegration#initialize} is normally only called once, from
 * {@code EliteMobs#syncInitialization} during EliteMobs' own {@code onEnable}. TrinityForge's
 * {@code dungeonWorldRegistry} (see {@code TrinityForge#onEnable}) is a brand-new, empty instance every
 * time TrinityForge itself (re)enables. If an operator reloads TrinityForge alone (e.g. a plugin-manager
 * {@code /plugman reload TrinityForge}) while EliteMobs keeps running, EliteMobs never re-runs
 * {@link TrinityForgeIntegration#initialize}, so it keeps holding a reference to the OLD
 * {@code ConfigManager}/services and never replays its known dungeon-world UUIDs into the NEW registry —
 * the registry stays empty and dungeon-only EXP silently stops working until EliteMobs itself restarts.
 * <p>
 * This listener watches for TrinityForge's own {@link PluginEnableEvent} and re-runs
 * {@link TrinityForgeIntegration#initialize} whenever it fires. {@code initialize} re-detects the plugin,
 * re-caches every service handle and replays every currently-known dungeon-world UUID
 * ({@code EliteMobsWorld#getAllWorldUUIDs}) into whatever registry TrinityForge exposes at that moment —
 * calling it repeatedly is safe (re-assigning the cached handles and re-registering already-known UUIDs
 * into a {@code Set}-backed registry are both idempotent, see {@code DungeonWorldRegistry#register}).
 * <p>
 * No-op (and harmless) on every other plugin's enable event, and safe even on EliteMobs' own initial
 * enable (TrinityForge's {@code softdepend} ordering means TrinityForge is already enabled by then, so
 * its {@link PluginEnableEvent} already fired before this listener was registered — the direct call in
 * {@code EliteMobs#syncInitialization} covers that first-boot case).
 */
public class TrinityForgeReloadListener implements Listener {

    private static final String TRINITYFORGE_PLUGIN_NAME = "TrinityForge";

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!TRINITYFORGE_PLUGIN_NAME.equals(event.getPlugin().getName())) return;
        Logger.info("TrinityForge (re)enabled — re-initializing the TrinityForge integration so dungeon-world "
                + "registration and cached service handles stay in sync.");
        TrinityForgeIntegration.initialize(MetadataHandler.PLUGIN);
    }
}
