package com.magmaguy.elitemobs.instanced.dungeons;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.config.DungeonsConfig;
import com.magmaguy.elitemobs.config.SpecialItemSystemsConfig;
import com.magmaguy.elitemobs.config.contentpackages.ContentPackagesConfigFields;
import com.magmaguy.elitemobs.dungeons.WorldDungeonPackage;
import com.magmaguy.elitemobs.instanced.WorldOperationQueue;
import com.magmaguy.elitemobs.menus.ItemEnchantmentMenu;
import com.magmaguy.elitemobs.utils.WorldInstantiator;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantmentDungeonInstance extends DungeonInstance {
    @Getter
    @Setter
    Player player;
    @Getter
    @Setter
    private ItemStack upgradedItem;
    @Getter
    @Setter
    private ItemStack currentItem;

    public EnchantmentDungeonInstance(ContentPackagesConfigFields contentPackagesConfigFields,
                                      Location lobbyLocation,
                                      Location startLocation,
                                      World world,
                                      Player player,
                                      String difficultyName) {
        super(contentPackagesConfigFields,
                lobbyLocation,
                startLocation,
                world,
                player,
                difficultyName);
        this.player = player;
    }

    public static boolean setupRandomEnchantedChallengeDungeon(Player player, ItemStack upgradedItem, ItemStack itemFromInventory) {
        List<ContentPackagesConfigFields> contentPackagesConfigFieldsList = new ArrayList<>();
        WorldDungeonPackage.getEmPackages().values().stream().forEach(emPackage -> {if (emPackage.isInstalled() && emPackage.getContentPackagesConfigFields().isEnchantmentChallenge()) contentPackagesConfigFieldsList.add(emPackage.getContentPackagesConfigFields());});
        if (contentPackagesConfigFieldsList.isEmpty()) {
            player.sendMessage(DungeonsConfig.getEnchantNoChallengeMessage());
            return false;
        }
        ContentPackagesConfigFields contentPackagesConfigFields = contentPackagesConfigFieldsList.get(ThreadLocalRandom.current().nextInt(0, contentPackagesConfigFieldsList.size()));
        String instancedWordName = WorldInstantiator.getNewWorldName(contentPackagesConfigFields.getWorldName());

        if (!launchEvent(contentPackagesConfigFields, instancedWordName, player)) return false;

        // Clone items before passing to lambda to avoid modification
        ItemStack upgradedItemClone = upgradedItem.clone();
        ItemStack currentItemClone = itemFromInventory.clone();

        WorldOperationQueue.queueOperation(
                player,
                () -> cloneWorldFiles(contentPackagesConfigFields, instancedWordName, player) != null,
                () -> {
                    DungeonInstance dungeonInstance = initializeInstancedWorld(contentPackagesConfigFields, instancedWordName, player, (String) contentPackagesConfigFields.getDifficulties().get(0).get("name"));
                    if (dungeonInstance instanceof EnchantmentDungeonInstance enchantmentDungeonInstance) {
                        enchantmentDungeonInstance.setUpgradedItem(upgradedItemClone);
                        enchantmentDungeonInstance.setCurrentItem(currentItemClone);
                    }
                },
                contentPackagesConfigFields.getName()
        );

        return true;
    }

    /**
     * このインスタンスが「エンチャントの賭け」を伴っているか(2026-08-18 実サーバ報告の修正)。
     *
     * <p>{@code enchantmentChallenge: true} のコンテンツパッケージは2通りの入口を持つ:
     * <ul>
     *   <li>{@link #setupRandomEnchantedChallengeDungeon} — エンチャントメニュー経由。ここでだけ
     *       {@code upgradedItem}(成功時に渡す強化後アイテム)と {@code currentItem}(失敗時に返す元アイテム)
     *       がセットされる。</li>
     *   <li>{@link DungeonInstance#setupInstancedDungeon} — 通常のダンジョン入場(TrinityForge の鍵ゲート /
     *       ダンジョンブラウザ)。{@code initializeInstancedWorld} は
     *       {@code isEnchantmentChallenge()} を見て <b>この</b> クラスを生成するので、賭けアイテムは
     *       <b>両方 null のまま</b>になる。</li>
     * </ul>
     *
     * <p>後者で {@link #victory()}/{@link #defeat()} がそのまま走ると
     * {@code currentItem.getItemMeta()} で NPE になり、<b>例外が呼び出し元まで抜けて脱出処理を丸ごと飛ばす</b>。
     * 実際に 2026-08-18 17:45 に発生した障害はこれで、
     * {@code InstancePlayerManager#playerDeath} の defeat() が投げた結果その直後の
     * 「元の位置へテレポートして戻す」が実行されず、プレイヤーは {@code players} にも
     * {@code spectators} にも属さない状態でインスタンスワールドに取り残された
     * ({@code removeAnyKind} が何もしなくなるため {@code /em quit} でも戻れない)。
     * ワールドも「中に人が居る」ため削除に失敗して残り続ける。
     */
    private boolean hasEnchantmentStake() {
        return player != null && upgradedItem != null && currentItem != null;
    }

    @Override
    public void endMatch() {
        if (!hasEnchantmentStake()) {
            // 賭けアイテムが無い＝通常のダンジョンとして入場された個体。上位(DungeonInstance)の
            // 終了処理に従い、参加者への通知と規定時間後の解体を通常どおり行う。
            super.endMatch();
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                removeInstance();
            }
        }.runTaskLater(MetadataHandler.PLUGIN, 20 * 10);
    }

    @Override
    protected void victory() {
        super.victory();
        if (!hasEnchantmentStake()) return;
        player.sendMessage(DungeonsConfig.getEnchantChallengeCompleteMessage());
        player.sendMessage(DungeonsConfig.getEnchantChallengeSuccessMessage());
        ItemEnchantmentMenu.broadcastEnchantmentMessage(upgradedItem, player, SpecialItemSystemsConfig.getSuccessAnnouncement());
        //scanCurrentItemForRemoval();
        HashMap<Integer, ItemStack> leftOvers = player.getInventory().addItem(upgradedItem);
        if (!leftOvers.isEmpty()) player.getWorld().dropItem(player.getLocation(), upgradedItem);
    }

    @Override
    protected void defeat() {
        super.defeat();
        if (!hasEnchantmentStake()) return;
        if (ThreadLocalRandom.current().nextDouble() < SpecialItemSystemsConfig.getCriticalFailureChanceDuringChallengeChance()) {
            player.sendMessage(DungeonsConfig.getEnchantCriticalFailureMessage().replace("$item", currentItem.getItemMeta().getDisplayName()));
            ItemEnchantmentMenu.broadcastEnchantmentMessage(upgradedItem, player, SpecialItemSystemsConfig.getCriticalFailureAnnouncement());
            //scanCurrentItemForRemoval();
        } else {
            player.sendMessage(DungeonsConfig.getEnchantChallengeFailedMessage().replace("$item", currentItem.getItemMeta().getDisplayName()));
            HashMap<Integer, ItemStack> leftOvers = player.getInventory().addItem(currentItem);
            if (!leftOvers.isEmpty()) player.getWorld().dropItem(player.getLocation(), currentItem);
        }
    }

    /*
    private void scanCurrentItemForRemoval() {
        if (player.getInventory().contains(currentItem))
            player.getInventory().remove(currentItem);
        else if (player.getInventory().getHelmet() != null && player.getInventory().getHelmet().equals(currentItem))
            player.getInventory().setHelmet(null);
        else if (player.getInventory().getChestplate() != null && player.getInventory().getChestplate().equals(currentItem))
            player.getInventory().setChestplate(null);
        else if (player.getInventory().getLeggings() != null && player.getInventory().getLeggings().equals(currentItem))
            player.getInventory().setLeggings(null);
        else if (player.getInventory().getBoots() != null && player.getInventory().getBoots().equals(currentItem))
            player.getInventory().setBoots(null);
        else if (player.getInventory().getItemInOffHand().equals(currentItem))
            player.getInventory().setItemInOffHand(null);
    }
     */

}
