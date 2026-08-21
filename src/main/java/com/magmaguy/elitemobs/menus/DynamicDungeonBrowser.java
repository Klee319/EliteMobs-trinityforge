package com.magmaguy.elitemobs.menus;

import com.magmaguy.elitemobs.commands.DungeonCommands;
import com.magmaguy.elitemobs.config.DungeonsConfig;
import com.magmaguy.elitemobs.config.menus.premade.PlayerStatusMenuConfig;
import com.magmaguy.elitemobs.dungeons.DynamicDungeonPackage;
import com.magmaguy.elitemobs.dungeons.EMPackage;
import com.magmaguy.elitemobs.instanced.MatchInstance;
import com.magmaguy.elitemobs.instanced.dungeons.DungeonInstance;
import com.magmaguy.elitemobs.instanced.dungeons.DynamicDungeonInstance;
import com.magmaguy.elitemobs.skills.CombatLevelCalculator;
import com.magmaguy.magmacore.util.ItemStackGenerator;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DynamicDungeonBrowser extends EliteMenu {
    private static final HashMap<Inventory, DynamicDungeonBrowser> inventories = new HashMap<>();
    private static final int BACK_SLOT = 17;

    public static void shutdown() {
        inventories.clear();
    }

    @Getter
    private final EMPackage emPackage;
    private final DungeonCommands.TeleportMenuSource teleportMenuSource;
    // TrinityForge 変更(2026-08-21): レベル選択メニューを廃止したので levelSlots は無い。
    // 難易度は「戦闘レベルの何%で潜るか」そのものになったため、レベルを別に選ばせる意味が消えた。
    private final List<Integer> difficultySlots = List.of(2, 4, 6, 0, 8, 1, 3, 5, 7);
    private final List<Integer> validSlots = new ArrayList<>(List.of(18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
            31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53));
    private List<DynamicDungeonInstance> instancesList = new ArrayList<>();

    public DynamicDungeonBrowser(Player player, String dynamicDungeonName) {
        this(player, dynamicDungeonName, DungeonCommands.TeleportMenuSource.NONE);
    }

    public DynamicDungeonBrowser(Player player, String dynamicDungeonName, DungeonCommands.TeleportMenuSource teleportMenuSource) {
        EMPackage emPackage = EMPackage.getEmPackages().get(dynamicDungeonName);
        this.emPackage = emPackage;
        this.teleportMenuSource = teleportMenuSource;

        if (!(emPackage instanceof DynamicDungeonPackage)) {
            player.sendMessage(DungeonsConfig.getInvalidDynamicDungeonMessage());
            return;
        }

        showDifficultySelectionMenu(player, dynamicDungeonName);
    }

    private ItemStack spectatorItem(DynamicDungeonInstance dungeonInstance) {
        List<String> lore = new ArrayList<>();
        lore.add(DungeonsConfig.getDungeonBrowserLevelLabel().replace("$level", String.valueOf(dungeonInstance.getSelectedLevel())));
        lore.add(DungeonsConfig.getDungeonBrowserPlayersLabel());
        dungeonInstance.getPlayers().forEach(player -> lore.add(DungeonsConfig.getDungeonBrowserPlayerEntryFormat().replace("$playerName", player.getDisplayName())));
        return ItemStackGenerator.generateItemStack(
                Material.ORANGE_STAINED_GLASS_PANE,
                DungeonsConfig.getDungeonJoinAsSpectatorText().replace("$dungeonName", dungeonInstance.getContentPackagesConfigFields().getName()),
                lore);
    }

    private ItemStack playerItem(DynamicDungeonInstance dungeonInstance) {
        List<String> lore = new ArrayList<>();
        lore.add(DungeonsConfig.getDungeonBrowserLevelLabel().replace("$level", String.valueOf(dungeonInstance.getSelectedLevel())));
        lore.add(DungeonsConfig.getDungeonBrowserPlayersLabel());
        dungeonInstance.getPlayers().forEach(player -> lore.add(DungeonsConfig.getDungeonBrowserPlayerEntryFormat().replace("$playerName", player.getDisplayName())));
        return ItemStackGenerator.generateItemStack(
                Material.GREEN_STAINED_GLASS_PANE,
                DungeonsConfig.getDungeonJoinAsPlayerText().replace("$dungeonName", dungeonInstance.getContentPackagesConfigFields().getName()),
                lore);
    }

    private void showDifficultySelectionMenu(Player player, String dynamicDungeonName) {
        // TrinityForge 変更(2026-08-21): この難易度メニューがダイナミックダンジョンの入口になった
        // (ユーザー要望「ノーマル/ハード/ミシックを戦闘レベル±5から選ぶのではなく、戦闘レベルの
        //  100%/75%/50% から選んでそのレベルを反映してほしい」)。
        //
        // ⚠ 既存インスタンスへの参加枠は、廃止したレベル選択メニューから【ここへ移設してある】。
        //   移設し忘れると「立っているインスタンスへ合流する手段が GUI から丸ごと消える」ので、
        //   このメニューを触るときは validSlots のループを絶対に落とさないこと。
        int baseLevel = Math.max(1, CombatLevelCalculator.calculateCombatLevel(player.getUniqueId()));

        instancesList = DungeonInstance.getDungeonInstances().stream()
                .filter(instance -> instance instanceof DynamicDungeonInstance)
                .filter(instance -> instance.getContentPackagesConfigFields().getFilename().equals(dynamicDungeonName))
                .map(instance -> (DynamicDungeonInstance) instance)
                .collect(Collectors.toList());

        // 既存インスタンスがあるときだけ 54 枠へ広げる(下段 18-53 が参加枠)。
        int slots = instancesList.isEmpty() ? hasBackButton() ? 18 : 9 : 54;
        Inventory inventory = Bukkit.createInventory(player, slots,
                DungeonsConfig.getDynamicDungeonDifficultySelectionMenuTitle());

        int difficultyCounter = 0;
        for (Map map : emPackage.getContentPackagesConfigFields().getDifficulties()) {
            if (difficultyCounter >= difficultySlots.size()) break;
            String difficultyName = String.valueOf(map.get("name"));
            int percent = DungeonsConfig.getDynamicDungeonCombatLevelPercent(difficultyName);
            int mobLevel = DungeonsConfig.resolveDynamicDungeonMobLevel(baseLevel, difficultyName);

            List<String> description = new ArrayList<>();
            for (String string : DungeonsConfig.getInstancedDungeonDescription())
                description.add(string.replace("$dungeonName", emPackage.getContentPackagesConfigFields().getName()));
            description.add(DungeonsConfig.getDynamicDungeonDifficultySelectionMobLevel()
                    .replace("$level", String.valueOf(mobLevel))
                    .replace("$percent", String.valueOf(percent)));

            // 廃止したレベル選択メニューの色分けを踏襲する(緑=楽 / 黄=中 / 橙=きつい)。
            Material material = percent >= 100 ? Material.ORANGE_STAINED_GLASS_PANE
                    : percent >= 75 ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.LIME_STAINED_GLASS_PANE;

            inventory.setItem(difficultySlots.get(difficultyCounter), ItemStackGenerator.generateItemStack(
                    material,
                    DungeonsConfig.getInstancedDungeonTitle().replace("$difficulty",
                            DungeonsConfig.getDynamicDungeonDifficultyDisplayName(difficultyName)),
                    description));

            difficultyCounter++;
        }

        for (int i = 0; i < instancesList.size() && i < validSlots.size(); i++) {
            DynamicDungeonInstance instance = instancesList.get(i);
            ItemStack itemStack = null;
            if (instance.getState().equals(MatchInstance.InstancedRegionState.WAITING))
                itemStack = playerItem(instance);
            else if (DungeonsConfig.isAllowSpectatorsInInstancedContent())
                itemStack = spectatorItem(instance);
            if (itemStack != null)
                inventory.setItem(validSlots.get(i), itemStack);
        }

        if (hasBackButton()) {
            inventory.setItem(BACK_SLOT, PlayerStatusMenuConfig.getBackItem());
        }

        player.openInventory(inventory);
        inventories.put(inventory, this);
    }

    private boolean hasBackButton() {
        return teleportMenuSource != DungeonCommands.TeleportMenuSource.NONE;
    }

    public static class DynamicDungeonBrowserEvents implements Listener {
        @EventHandler
        public void onInventoryInteract(InventoryClickEvent event) {
            if (!isEliteMenu(event, inventories.keySet())) return;
            event.setCancelled(true);
            if (!isTopMenu(event)) return;
            if (event.getCurrentItem() == null || event.getCurrentItem().getType().equals(Material.AIR)) return;

            DynamicDungeonBrowser browser = inventories.get(event.getInventory());
            Player player = (Player) event.getWhoClicked();

            if (browser.hasBackButton() && event.getSlot() == BACK_SLOT) {
                event.getWhoClicked().closeInventory();
                DungeonCommands.reopenTeleportBrowser(player, browser.teleportMenuSource);
                return;
            }

            event.getWhoClicked().closeInventory();

            if (browser.difficultySlots.contains(event.getSlot())) {
                int difficultyIndex = browser.difficultySlots.indexOf(event.getSlot());
                List<Map<String, Object>> difficulties =
                        browser.getEmPackage().getContentPackagesConfigFields().getDifficulties();
                if (difficulties == null || difficultyIndex >= difficulties.size()) return;
                String difficultyName = String.valueOf(difficulties.get(difficultyIndex).get("name"));

                // 挑戦レベルは【ここで】確定させる。表示に使ったレベルを持ち回さず引き直すのは、
                // メニューを開いてから押すまでに戦闘レベルが上がった場合でも表示と実物を一致させるため
                // (押した瞬間の戦闘レベルが正)。
                int baseLevel = Math.max(1, CombatLevelCalculator.calculateCombatLevel(player.getUniqueId()));
                int selectedLevel = DungeonsConfig.resolveDynamicDungeonMobLevel(baseLevel, difficultyName);

                DynamicDungeonInstance.setupDynamicDungeon(
                        player,
                        browser.getEmPackage().getContentPackagesConfigFields().getFilename(),
                        difficultyName,
                        selectedLevel);
            } else if (browser.validSlots.contains(event.getSlot())) {
                // Player clicked on an existing instance to join
                int instanceIndex = browser.validSlots.indexOf(event.getSlot());
                if (instanceIndex < browser.instancesList.size()) {
                    DynamicDungeonInstance dungeonInstance = browser.instancesList.get(instanceIndex);
                    switch (dungeonInstance.getState()) {
                        case ONGOING, STARTING -> {
                            if (DungeonsConfig.isAllowSpectatorsInInstancedContent())
                                dungeonInstance.addSpectator(player, false);
                        }
                        case WAITING -> dungeonInstance.addNewPlayer(player);
                        case COMPLETED, COMPLETED_VICTORY, COMPLETED_DEFEAT ->
                                player.sendMessage(DungeonsConfig.getMatchAlreadyEndedMessage());
                    }
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            inventories.remove(event.getInventory());
        }
    }
}
