package com.magmaguy.elitemobs.items;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.api.utils.EliteItemManager;
import com.magmaguy.elitemobs.config.AdventurersGuildConfig;
import com.magmaguy.elitemobs.config.menus.premade.SkillBonusMenuConfig;
import com.magmaguy.elitemobs.playerdata.database.PlayerData;
import com.magmaguy.elitemobs.skills.SkillType;
import com.magmaguy.elitemobs.skills.SkillXPCalculator;
import com.magmaguy.magmacore.util.ChatColorConverter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Provides utility methods for skill-based gear restrictions.
 * The actual restriction enforcement is handled by PlayerItem's equipment update system.
 */
public class GearRestrictionHandler {

    // Track players who have been warned recently to avoid spam
    private static final Set<UUID> recentWarnings = new HashSet<>();

    public static void shutdown() {
        recentWarnings.clear();
    }

    /**
     * Checks if a player can equip an item based on their skill level.
     *
     * @param player    The player trying to equip the item
     * @param itemStack The item being equipped
     * @return true if the player can equip, false if restricted
     */
    public static boolean canEquip(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) return true;
        // Fork: coarse TrinityForge use-level gate (config-gated, default off; fails open). Independent of
        // EliteMobs' own skill-based gear restriction below, so it can apply even when that is disabled.
        if (!com.magmaguy.elitemobs.trinityforge.TrinityForgeUseRequirement.canUse(player, itemStack)) return false;
        // Phase 6(2026-07-18): 武器スキルは無力化済み(PlayerData.getSkillXP は常時0)。スキル依存の装備制限を
        // そのまま残すと、全プレイヤーのスキルレベルが0となり Lv20超の elite アイテムを誰も装備できなくなる
        // (下の playerSkillLevel(=0) >= itemLevel が常に false)。ロックアウトを避けるため、スキル基準の装備
        // 制限は無効化する。進行の門番はTF/ValhallaMMOへ一本化。TFの使用レベルゲート(上の canUse)は独立して有効。
        return true;
    }

    /**
     * Gets the effective item level for combat purposes.
     * If the player can't use the item, returns 0 (no elite damage).
     */
    public static int getEffectiveItemLevel(Player player, ItemStack itemStack) {
        if (!AdventurersGuildConfig.isSkillBasedGearRestriction()) {
            return EliteItemManager.getRoundedItemLevel(itemStack);
        }

        if (!canEquip(player, itemStack)) {
            return 0; // Item provides no elite bonuses
        }

        return EliteItemManager.getRoundedItemLevel(itemStack);
    }

    /**
     * Sends the restriction message to a player.
     * Called by PlayerItem when an item is rejected.
     */
    public static void sendRestrictionMessage(Player player, ItemStack itemStack) {
        if (!AdventurersGuildConfig.isSkillBasedGearRestriction()) return;

        SkillType skillType = SkillType.fromMaterialIncludingArmor(itemStack.getType());
        if (skillType == null) return;

        // Avoid spamming warnings
        if (recentWarnings.contains(player.getUniqueId())) return;
        recentWarnings.add(player.getUniqueId());

        // Remove from warnings after 2 seconds
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            MetadataHandler.PLUGIN,
            () -> recentWarnings.remove(player.getUniqueId()),
            40L
        );

        int itemLevel = EliteItemManager.getRoundedItemLevel(itemStack);
        long skillXP = PlayerData.getSkillXP(player.getUniqueId(), skillType);
        int playerSkillLevel = SkillXPCalculator.levelFromTotalXP(skillXP);

        String message = AdventurersGuildConfig.getGearRestrictionMessage()
                .replace("$itemLevel", String.valueOf(itemLevel))
                .replace("$skillLevel", String.valueOf(playerSkillLevel))
                .replace("$skillType", SkillBonusMenuConfig.getSkillTypeDisplayName(skillType));

        player.sendMessage(ChatColorConverter.convert(message));
    }
}
