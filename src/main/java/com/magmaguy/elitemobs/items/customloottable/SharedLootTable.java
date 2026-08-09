package com.magmaguy.elitemobs.items.customloottable;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.api.utils.EliteItemManager;
import com.magmaguy.elitemobs.config.CommandMessagesConfig;
import com.magmaguy.elitemobs.config.InitializeConfig;
import com.magmaguy.elitemobs.items.EliteItemLore;
import com.magmaguy.elitemobs.items.customenchantments.SoulbindEnchantment;
import com.magmaguy.elitemobs.items.customitems.CustomItem;
import com.magmaguy.elitemobs.menus.LootMenu;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.magmacore.util.ChatColorConverter;
import com.magmaguy.magmacore.util.Logger;
import com.magmaguy.magmacore.util.SpigotMessage;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SharedLootTable {
    @Getter
    private static final HashMap<EliteEntity, SharedLootTable> sharedLootTables = new HashMap<>();

    public static void shutdown() {
        sharedLootTables.clear();
    }

    private final int durationInSeconds = 60;
    @Getter
    private final List<ItemStack> loot = new ArrayList<>();
    private final EliteEntity eliteEntity;
    private final List<Player> damagers;
    private final List<LootMenu> lootMenus = new ArrayList<>();
    private final HashMap<Player, PlayerTable> playerTables = new HashMap<>();
    /**
     * TrinityForge が入れた戦利品 (2026-08-09)。EliteMobs 側の後処理
     * (ソウルバインド / EliteItemLore の書き直し / エリートレベルの刻印) を通してはいけないものを
     * インスタンス同一性で覚えておく。{@code equals} ではなく同一性なのは、同じ素材の
     * スタックが EliteMobs 側と TrinityForge 側の両方から入ることがあるため。
     */
    private final Set<ItemStack> trinityForgeLoot = Collections.newSetFromMap(new IdentityHashMap<>());

    public SharedLootTable(EliteEntity eliteEntity) {
        this.eliteEntity = eliteEntity;

        this.damagers = eliteEntity.getDamagers().keySet().stream().toList();
        sharedLootTables.put(eliteEntity, this);
        damagers.forEach(damager -> lootMenus.add(new LootMenu(damager, this, getPlayerTable(damager))));
        if (damagers.size() > 1)
            Bukkit.getScheduler().runTaskLater(MetadataHandler.PLUGIN, this::messagePlayers, 1);
        endLoot();
    }

    private void messagePlayers() {
        damagers.forEach(player -> {
            Logger.sendSimpleMessage(player, CommandMessagesConfig.getLootVoteSeparator());
            player.spigot().sendMessage(
                    SpigotMessage.simpleMessage(CommandMessagesConfig.getLootVoteMessage()),
                    SpigotMessage.commandHoverMessage(
                            InitializeConfig.getEmLootDisplay(),
                            InitializeConfig.getEmLootHover(),
                            "/em loot"),
                    SpigotMessage.simpleMessage(CommandMessagesConfig.getLootVoteMessageSuffix()
                            .replace("$count", String.valueOf(loot.size()))));
            Logger.sendSimpleMessage(player, CommandMessagesConfig.getLootVoteSeparator());
        });
    }

    public void addLoot(ItemStack itemStack) {
        loot.add(itemStack);
    }

    /**
     * TrinityForge の追加ドロップを共有戦利品テーブルへ入れる (2026-08-09)。
     * <p>
     * {@link #addLoot(ItemStack)} との違いは配布時の後処理だけで、need/greed の投票と
     * 抽選そのものは同じ。TrinityForge のアイテムはステータス / 品質 / ロアを TrinityForge が
     * 一元管理しているため、EliteMobs 側でソウルバインドを付けたりロアを書き直したりすると
     * 表示も内部データも壊れる。詳細は {@link #rollLoot(ItemStack, java.util.List)}。
     */
    public void addTrinityForgeLoot(ItemStack itemStack) {
        loot.add(itemStack);
        trinityForgeLoot.add(itemStack);
    }

    private void endLoot() {
        if (damagers.size() < 2) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    distribute();
                }
            }.runTaskLater(MetadataHandler.PLUGIN, 1);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                distribute();
            }
        }.runTaskLater(MetadataHandler.PLUGIN, 20L * durationInSeconds);
    }

    private void distribute() {
        for (ItemStack lootInstance : loot) {
            List<Player> needPlayers = new ArrayList<>();
            for (PlayerTable playerTable : playerTables.values())
                for (ItemStack neededLoot : playerTable.getNeedItems()) {
                    if (neededLoot.isSimilar(lootInstance)) {
                        needPlayers.add(playerTable.player);
                    }
                }
            if (needPlayers.isEmpty()) {
                rollLoot(lootInstance, damagers);
            } else rollLoot(lootInstance, needPlayers);
        }
        sharedLootTables.remove(eliteEntity);
        lootMenus.forEach(LootMenu::removeMenu);
    }

    /**
     * 当選者を1人選んでアイテムを渡す。
     * <p>
     * <b>TrinityForge の追加ドロップ ({@link #addTrinityForgeLoot(ItemStack)} で入れたもの) には
     * EliteMobs 側の後処理を一切掛けない (2026-08-09)。</b> 3つとも TrinityForge のアイテムを壊す:
     * <ul>
     *   <li>{@code SoulbindEnchantment} — TrinityForge の追加ドロップは素材が主で、束縛すると
     *       取引も受け渡しもできなくなる。束縛するかどうかは TrinityForge 側の設定が決める。</li>
     *   <li>{@code EliteItemLore} — TrinityForge はロアを rollSeed + 品質から毎回導出するので、
     *       EliteMobs 形式のロアで上書きすると表示が二重になり {@code /trinityforge reload} でも直らない。</li>
     *   <li>{@code EliteItemManager#setEliteLevel} — EliteMobs のアイテムレベルは装備tier由来で、
     *       TrinityForge の必要レベルとは別物。刻むと使用制限の判定がずれる。</li>
     * </ul>
     * 抽選 (need/greed と当選者の選び方) は EliteMobs 由来のアイテムと完全に同じ。
     */
    private void rollLoot(ItemStack item, List<Player> players) {
        Player player = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        if (!trinityForgeLoot.contains(item)) {
            SoulbindEnchantment.addEnchantment(item, player);
            new EliteItemLore(item, false);
            int newLevel = CustomItem.limitItemLevel(player, EliteItemManager.getRoundedItemLevel(item));
            EliteItemManager.setEliteLevel(item, newLevel);
        }
        HashMap<Integer, ItemStack> pendingItems = player.getInventory().addItem(item);
        if (!pendingItems.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), item);
        String itemName = displayNameOf(item);
        players.forEach(thisPlayer -> thisPlayer.sendMessage(
                player.getDisplayName() + ChatColor.GREEN + " received " + itemName + " !"));
    }

    /**
     * 配布メッセージ用の表示名。EliteMobs のアイテムは必ず表示名を持つが、TrinityForge の追加ドロップは
     * 素の素材 (表示名なし) のことがあり、そのまま {@code getDisplayName()} を読むと空文字になって
     * 「$player received  !」になる。表示名が無ければ材質名へ落とす。
     */
    private static String displayNameOf(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) return item.getItemMeta().getDisplayName();
        return item.getAmount() + "x " + item.getType().toString().toLowerCase().replace("_", " ");
    }

    public PlayerTable getPlayerTable(Player player) {
        PlayerTable playerTable = playerTables.get(player);
        if (playerTable == null) playerTable = new PlayerTable(player, this);
        return playerTable;
    }

    public class PlayerTable {
        @Getter
        private final List<ItemStack> needItems = new ArrayList<>();
        private final Player player;
        private final SharedLootTable sharedLootTable;

        public PlayerTable(Player player, SharedLootTable sharedLootTable) {
            this.player = player;
            this.sharedLootTable = sharedLootTable;
            playerTables.put(player, this);
        }

        public void addNeed(ItemStack itemStack) {
            needItems.add(itemStack);
            sharedLootTable.damagers.forEach(damager -> damager.sendMessage(ChatColorConverter.convert(
                    CommandMessagesConfig.getLootNeedMessage()
                            .replace("$player", player.getDisplayName())
                            .replace("$item", itemStack.getItemMeta().getDisplayName()))));
        }

        public void removeNeed(ItemStack itemStack) {
            needItems.remove(itemStack);
            sharedLootTable.damagers.forEach(damager -> damager.sendMessage(ChatColorConverter.convert(
                    CommandMessagesConfig.getLootGreedMessage()
                            .replace("$player", player.getDisplayName())
                            .replace("$item", itemStack.getItemMeta().getDisplayName()))));
        }
    }
}
