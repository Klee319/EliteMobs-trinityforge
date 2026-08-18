package com.magmaguy.elitemobs.mobconstructor.custombosses;

import com.google.common.collect.ArrayListMultimap;
import com.magmaguy.elitemobs.api.internal.RemovalReason;
import com.magmaguy.elitemobs.config.custombosses.CustomBossesConfig;
import com.magmaguy.elitemobs.config.custombosses.CustomBossesConfigFields;
import com.magmaguy.elitemobs.instanced.dungeons.DungeonInstance;
import com.magmaguy.elitemobs.instanced.dungeons.DynamicDungeonInstance;
import com.magmaguy.elitemobs.mobconstructor.PersistentMovingEntity;
import com.magmaguy.elitemobs.mobconstructor.PersistentObject;
import com.magmaguy.elitemobs.playerdata.ElitePlayerInventory;
import com.magmaguy.elitemobs.utils.ConfigurationLocation;
import com.magmaguy.magmacore.instance.MatchInstance;
import com.magmaguy.magmacore.util.AttributeManager;
import com.magmaguy.magmacore.util.Logger;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InstancedBossEntity extends RegionalBossEntity implements PersistentObject, PersistentMovingEntity {
    private static final ArrayListMultimap<String, InstancedBossContainer> instancedBossEntities = ArrayListMultimap.create();
    @Getter
    private  DungeonInstance dungeonInstance = null;
    @Getter
    private  MatchInstance matchInstance;
    @Getter @Setter
    private Set<Player> lockoutPlayers = new HashSet<>();

    public InstancedBossEntity(CustomBossesConfigFields customBossesConfigFields, Location location, DungeonInstance dungeonInstance) {
        super(customBossesConfigFields, location, false, true);
        this.dungeonInstance = dungeonInstance;
        super.setElitePowers(ElitePowerParser.parsePowers(customBossesConfigFields, this));
        if (level == -1) {
            // For dynamic dungeons, use the player-selected level instead of calculating from gear
            if (dungeonInstance instanceof DynamicDungeonInstance dynamicDungeonInstance) {
                // TrinityForge 変更(2026-08-18 W-80): 選んだレベルそのものではなく難易度補正込みの実効レベル。
                // normal は -5 / mythic は +5。素の EM では難易度は持ち込めるEMアイテムの tier 上限にしか
                // 効かず、TF は EliteMobs のアイテム体系を使わないので難易度が完全な no-op だった。
                level = dynamicDungeonInstance.getMobLevel();
            } else if (dungeonInstance.getPlayers().isEmpty()) {
                Logger.warn("Failed to get players for new instance when assigning dynamic level! The bosses will default to level 1.");
            } else {
                level = ElitePlayerInventory.getPlayer(dungeonInstance.getPlayers().stream().findFirst().get()).getNaturalMobSpawnLevel(true);
            }
        }
    }

    public InstancedBossEntity(CustomBossesConfigFields customBossesConfigFields, Location location, MatchInstance matchInstance, int level) {
        super(customBossesConfigFields, location, false, true);
        super.level = level;
        this.matchInstance = matchInstance;
        super.setElitePowers(ElitePowerParser.parsePowers(customBossesConfigFields, this));
//        if (level == -1) {
//            if (matchInstance.getPlayers().isEmpty())
//                Logger.warn("Failed to get players for new instance when assigning dynamic level! The bosses will default to level 1.");
//            else
//                level = ElitePlayerInventory.getPlayer(matchInstance.getPlayers().stream().findFirst().get()).getNaturalMobSpawnLevel(true);
//        }
    }

    public static CustomBossEntity createInstancedBossEntity(String filename, Location location, MatchInstance matchInstance, int level){
        CustomBossesConfigFields configFields = CustomBossesConfig.getCustomBoss(filename);
        if (configFields == null){

            Logger.warn("Failed to spawn instanced boss entity " + filename + " via API!");
            return null;
        }
        return new InstancedBossEntity(configFields, location, matchInstance, level);
    }

    public static void shutdown() {
        instancedBossEntities.clear();
    }

    public static void add(String stringLocation, CustomBossesConfigFields customBossesConfigFields) {
        String blueprintWorldName = stringLocation.split(",")[0];
        if (blueprintWorldName == null || blueprintWorldName.isEmpty()) {
            Logger.warn("Failed to get blueprint world location for custom boss " + customBossesConfigFields.getFilename() + " !");
            return;
        }
        instancedBossEntities.put(blueprintWorldName, new InstancedBossContainer(ConfigurationLocation.serialize(stringLocation, true), customBossesConfigFields));
    }

    public static List<InstancedBossEntity> initializeInstancedBosses(String blueprintWorldName, World newWorld, int playerCount, DungeonInstance dungeonInstance) {
        List<InstancedBossEntity> newDungeonList = new ArrayList<>();
        List<InstancedBossContainer> rawBosses = instancedBossEntities.get(blueprintWorldName);
        for (InstancedBossContainer containers : rawBosses) {
            Location newLocation = containers.getLocation().clone();
            newLocation.setWorld(newWorld);
            InstancedBossEntity newEntity = new InstancedBossEntity(containers.getCustomBossesConfigFields(), newLocation, dungeonInstance);
            newEntity.spawn(false);
            newDungeonList.add(newEntity);
        }
        return newDungeonList;
    }

    public void setNormalizedMaxHealth(int playerCount) {
        super.setNormalizedMaxHealth();
        if (dungeonInstance == null) return;
        if (playerCount < 2) return;
        double normalizedDungeonMaxHealth = super.getMaxHealth() * .75 * playerCount;
        super.maxHealth = normalizedDungeonMaxHealth;
        super.health = maxHealth;
        if (livingEntity != null) {
            AttributeManager.setAttribute(livingEntity, "generic_max_health", maxHealth);
            livingEntity.setHealth(maxHealth);
        }
    }

    @Override
    public void setMaxHealth() {
        super.setNormalizedMaxHealth();
        if (dungeonInstance == null) return;
        if (dungeonInstance.getPlayers().size() < 2) return;
        double normalizedDungeonMaxHealth = super.getMaxHealth() * .75 * dungeonInstance.getPlayers().size();
        super.maxHealth = normalizedDungeonMaxHealth;
        if (health == null) {
            if (livingEntity != null) livingEntity.setHealth(maxHealth);
            this.health = maxHealth;
        }
        //This is useful for phase boss entities that spawn in unloaded chunks and shouldn't full heal between phases, like in dungeons
        else if (livingEntity != null)
            livingEntity.setHealth(Math.min(health, AttributeManager.getAttributeBaseValue(livingEntity, "generic_max_health")));
    }

    public void setEntityLevel(int level) {
        this.level = level;
        setMaxHealth();
        // 2026-08-18: TF の MOB_LEVEL 刻印はスポーン時に1回書かれるだけなので、ここでレベルを
        // 変えても刻み直さないと TF はスポーン時の値を見続ける。TF はレベル差で経験値と
        // ドロップ確率を増減させるため、刻み直さないとダンジョンで選んだレベルが報酬に反映されない。
        com.magmaguy.elitemobs.trinityforge.TrinityForgeIntegration.restampMobLevel(livingEntity, level);
    }

    @Override
    public void remove(RemovalReason removalReason) {
        super.remove(removalReason);
        if (removalReason.equals(RemovalReason.WORLD_UNLOAD))
            if (persistentObjectHandler != null) {
                persistentObjectHandler.remove();
                persistentObjectHandler = null;
            }
    }

    private static class InstancedBossContainer {
        @Getter
        private final Location location;
        @Getter
        private final CustomBossesConfigFields customBossesConfigFields;

        public InstancedBossContainer(Location location, CustomBossesConfigFields customBossesConfigFields) {
            this.location = location;
            this.customBossesConfigFields = customBossesConfigFields;
        }
    }
}
