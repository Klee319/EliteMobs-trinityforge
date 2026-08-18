package com.magmaguy.elitemobs.instanced.dungeons;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.config.DungeonsConfig;
import com.magmaguy.elitemobs.config.contentpackages.ContentPackagesConfig;
import com.magmaguy.elitemobs.config.contentpackages.ContentPackagesConfigFields;
import com.magmaguy.elitemobs.dungeons.utility.DungeonUtils;
import com.magmaguy.elitemobs.instanced.WorldOperationQueue;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomMusic;
import com.magmaguy.elitemobs.mobconstructor.custombosses.InstancedBossEntity;
import com.magmaguy.elitemobs.quests.DynamicQuest;
import com.magmaguy.elitemobs.utils.ConfigurationLocation;
import com.magmaguy.elitemobs.utils.WorldInstantiator;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DynamicDungeonInstance extends DungeonInstance {
    @Getter
    private final int selectedLevel;

    public DynamicDungeonInstance(ContentPackagesConfigFields contentPackagesConfigFields,
                                  Location lobbyLocation,
                                  Location startLocation,
                                  World world,
                                  Player player,
                                  String difficultyName,
                                  int selectedLevel) {
        super(contentPackagesConfigFields, lobbyLocation, startLocation, world, player, difficultyName);
        this.selectedLevel = selectedLevel;

        // Recalculate level sync for dynamic dungeons based on the player-selected level
        recalculateLevelSyncForDynamicLevel(selectedLevel);

        new SetBossLevelsTask(this, selectedLevel).runTaskLater(MetadataHandler.PLUGIN, 20 * 4L);
    }

    /**
     * このワールドが動いているダイナミックダンジョンのインスタンスなら、それを返す(そうでなければ null)。
     *
     * <p>2026-08-18 (W-80) 追加。インスタンス内で新しく湧いた EliteMobs を「選んだ挑戦レベル」へ
     * 揃えるために {@link DynamicDungeonLevelListener} が使う。ワールド名で引くのは、インスタンスの
     * ワールドが毎回新しい連番名でクローンされるため({@code em_id_the_mines_1} など)、
     * ブループリント名では同定できないから。
     */
    public static DynamicDungeonInstance getForWorld(World world) {
        if (world == null) return null;
        for (DungeonInstance dungeonInstance : getDungeonInstances())
            if (dungeonInstance instanceof DynamicDungeonInstance dynamicDungeonInstance
                    && world.getName().equals(dungeonInstance.getInstancedWorldName()))
                return dynamicDungeonInstance;
        return null;
    }

    public static void setupDynamicDungeon(Player player, String dungeonConfigFieldsString, String difficultyName, int selectedLevel) {
        ContentPackagesConfigFields dynamicDungeonConfigFields = ContentPackagesConfig.getDungeonPackages().get(dungeonConfigFieldsString);
        if (dynamicDungeonConfigFields == null) {
            player.sendMessage(DungeonsConfig.getDynamicDungeonDataFailedMessage().replace("$dungeon", dungeonConfigFieldsString));
            return;
        }

        if (dynamicDungeonConfigFields.getPermission() != null && !dynamicDungeonConfigFields.getPermission().isEmpty())
            if (!player.hasPermission(dynamicDungeonConfigFields.getPermission())) {
                player.sendMessage(DungeonsConfig.getDynamicDungeonNoPermissionMessage());
                return;
            }

        // TrinityForge combat-level entry gate, checked before the (expensive) world clone and before any
        // participant/instance state is created. Keyed by the dungeon's content-package filename, not the
        // dynamically numbered instance world it is about to clone into (fork spec section 6).
        if (!com.magmaguy.elitemobs.trinityforge.TrinityForgeDungeonGateListener.previewDungeonEntryAllowed(
                player, dynamicDungeonConfigFields.getFilename()))
            return;

        String instancedWorldName = WorldInstantiator.getNewWorldName(dynamicDungeonConfigFields.getWorldName());

        if (!launchEvent(dynamicDungeonConfigFields, instancedWorldName, player)) return;

        WorldOperationQueue.queueOperation(
                player,
                () -> cloneWorldFiles(dynamicDungeonConfigFields, instancedWorldName, player) != null,
                () -> initializeDynamicWorld(dynamicDungeonConfigFields, instancedWorldName, player, difficultyName, selectedLevel),
                dynamicDungeonConfigFields.getName()
        );
    }

    protected static DynamicDungeonInstance initializeDynamicWorld(ContentPackagesConfigFields dynamicDungeonConfigFields,
                                                                   String instancedWorldName,
                                                                   Player player,
                                                                   String difficultyName,
                                                                   int selectedLevel) {
        World world = DungeonUtils.loadWorld(instancedWorldName, dynamicDungeonConfigFields.getEnvironment(), dynamicDungeonConfigFields);
        if (world == null) {
            player.sendMessage(DungeonsConfig.getDynamicDungeonWorldLoadFailedMessage());
            return null;
        }

        // Initialize dungeon music for this dynamic instanced world
        if (dynamicDungeonConfigFields.getSong() != null)
            new CustomMusic(dynamicDungeonConfigFields.getSong(), dynamicDungeonConfigFields, world);

        Location startLocation = ConfigurationLocation.serialize(dynamicDungeonConfigFields.getStartLocationString());
        startLocation.setWorld(world);
        Location lobbyLocation = ConfigurationLocation.serialize(dynamicDungeonConfigFields.getTeleportLocationString());
        if (lobbyLocation != null) lobbyLocation.setWorld(world);
        else lobbyLocation = startLocation;

        return new DynamicDungeonInstance(dynamicDungeonConfigFields, lobbyLocation, startLocation, world, player, difficultyName, selectedLevel);
    }

    @Override
    public boolean addNewPlayer(Player player) {
        if (!super.addNewPlayer(player)) return false;
        // Show additional info about the selected level for dynamic dungeons
        player.sendMessage(DungeonsConfig.getDynamicDungeonLevelSetMessage().replace("$level", String.valueOf(selectedLevel)));

        // Adapt player's active DynamicQuests to the dungeon's selected level
        DynamicQuest.adaptPlayerQuestsToLevel(player, selectedLevel);

        return true;
    }

    private class SetBossLevelsTask extends BukkitRunnable {
        private final DynamicDungeonInstance dynamicDungeonInstance;
        private final int level;

        public SetBossLevelsTask(DynamicDungeonInstance dynamicDungeonInstance, int level) {
            this.dynamicDungeonInstance = dynamicDungeonInstance;
            this.level = level;
        }

        /**
         * 2026-08-18 (W-80): 以前は {@link InstancedBossEntity} だけを引き上げていたが、
         * ブループリント配置ではない個体(ボスの召喚した増援、フェーズ2/3の本体、API 経由で湧いた個体、
         * 自然湧きの elite)がこの網から漏れていた。漏れた個体は {@code level: dynamic} の既定経路
         * ({@code CustomBossEntity#getDynamicLevel})に落ちて<b>近くのプレイヤーの EliteMobs 装備tier</b>で
         * レベルが決まる ── TF は EliteMobs のアイテム体系を使わないので、その tier は実質 0 で、
         * つまりレベル1相当の張りぼてになる。ここは3秒後に一度だけ走る保険なので、インスタンス内の
         * elite を種類で区別せず全部そろえる(常時の面倒は {@link DynamicDungeonLevelListener} が見る)。
         */
        @Override
        public void run() {
            World instanceWorld = getWorld();
            if (instanceWorld == null) return;
            instanceWorld.getEntities().forEach(entity -> {
                if (!(entity instanceof org.bukkit.entity.LivingEntity)) return;
                com.magmaguy.elitemobs.mobconstructor.EliteEntity eliteEntity =
                        com.magmaguy.elitemobs.entitytracker.EntityTracker.getEliteMobEntity(entity);
                if (eliteEntity == null) return;
                if (eliteEntity instanceof InstancedBossEntity boss
                        && boss.getDungeonInstance() != null
                        && boss.getDungeonInstance() != dynamicDungeonInstance) {
                    // 別インスタンスのボスが同じワールドに居ることは無いはずだが、居たら触らない。
                    return;
                }
                DynamicDungeonLevelListener.applySelectedLevel(eliteEntity, level);
            });
        }
    }
}
