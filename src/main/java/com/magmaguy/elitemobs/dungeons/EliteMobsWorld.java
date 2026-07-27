package com.magmaguy.elitemobs.dungeons;

import com.magmaguy.elitemobs.config.contentpackages.ContentPackagesConfigFields;
import com.magmaguy.elitemobs.trinityforge.TrinityForgeIntegration;
import lombok.Getter;
import org.bukkit.Bukkit;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class EliteMobsWorld {
    private static final HashMap<UUID, EliteMobsWorld> eliteMobsWorlds = new HashMap<>();
    /**
     * Every dungeon-instance world UUID registered with TrinityForge, INCLUDING {@code protect:false}
     * worlds that never enter {@link #eliteMobsWorlds}. This is the authoritative source for the
     * TrinityForge startup replay ({@link #getAllWorldUUIDs()}) so a {@code protect:false} dungeon
     * loaded before TrinityForge finished detecting itself is not left out of the dungeon-only-EXP view.
     * All mutations happen on the main thread (world create/destroy), matching {@link #eliteMobsWorlds}.
     */
    private static final HashSet<UUID> registeredWorldUUIDs = new HashSet<>();
    @Getter
    private final ContentPackagesConfigFields contentPackagesConfigFields;
    @Getter
    private boolean allowExplosions;

    private EliteMobsWorld(UUID worldUUID, ContentPackagesConfigFields contentPackagesConfigFields) {
        this.contentPackagesConfigFields = contentPackagesConfigFields;
        // TrinityForge integration (fork spec item 1): tell TrinityForge this world is a dungeon
        // instance so its combat-level entry gate and dungeon-only EXP gate recognize it. No-op when
        // TrinityForge is absent. Registered BEFORE the isProtect() early-return on purpose: EXP
        // eligibility must NOT be coupled to the WorldGuard protection flag, otherwise a `protect:false`
        // dungeon would become a dungeon-only-exp dead zone (players earn no combat/armor/magic EXP in it).
        // The eliteMobsWorlds map membership (EM's own protection/explosion semantics) stays gated on
        // isProtect() below — only TrinityForge's dungeon-world view is broadened here.
        TrinityForgeIntegration.registerDungeonWorld(worldUUID);
        registeredWorldUUIDs.add(worldUUID);
        if (!contentPackagesConfigFields.isProtect()) {
            return;
        }
        this.allowExplosions = contentPackagesConfigFields.isAllowExplosions();
        eliteMobsWorlds.put(worldUUID, this);
    }

    public static void shutdown() {
        eliteMobsWorlds.keySet().removeIf(worldUUID -> Bukkit.getWorld(worldUUID) == null);
        registeredWorldUUIDs.removeIf(worldUUID -> Bukkit.getWorld(worldUUID) == null);
    }

    @Nullable
    public static EliteMobsWorld getEliteMobsWorld(UUID worldUUID) {
        return eliteMobsWorlds.get(worldUUID);
    }

    public static boolean isEliteMobsWorld(UUID worldUUID) {
        return eliteMobsWorlds.containsKey(worldUUID);
    }

    /**
     * @return a defensive copy of every currently-registered dungeon world's UUID. Used to replay
     * TrinityForge registration at startup for worlds created before TrinityForge finished detecting
     * itself (see {@code TrinityForgeIntegration#initialize}).
     */
    public static Set<UUID> getAllWorldUUIDs() {
        return Set.copyOf(registeredWorldUUIDs);
    }

    public static void create(UUID woldUUID, ContentPackagesConfigFields contentPackagesConfigFields) {
        new EliteMobsWorld(woldUUID, contentPackagesConfigFields);
    }

    public static void destroy(UUID worldUUID) {
        eliteMobsWorlds.remove(worldUUID);
        registeredWorldUUIDs.remove(worldUUID);
        // TrinityForge integration (fork spec item 1): mirror the removal so a destroyed instance's
        // world is no longer treated as a dungeon by TrinityForge. No-op when TrinityForge is absent.
        TrinityForgeIntegration.unregisterDungeonWorld(worldUUID);
    }

}
