package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.combat.SymmetricCombatService;
import com.trinityforge.pdc.ItemData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;

/**
 * Coarse use-level gate backed by TrinityForge {@code ItemData.useLevelRequirement()} (fork spec section 8,
 * "GearRestrictionHandler -&gt; require skill-tree level").
 * <p>
 * IMPORTANT API LIMITATION: TrinityForge's public facade does not currently expose per-skill ValhallaMMO
 * levels — only {@link SymmetricCombatService#combatLevelOf(java.util.UUID)}, a combat-level average. A
 * faithful per-skill check (compare {@code useSkill()} against that skill's level) is therefore not
 * possible through the public API yet. As a documented placeholder, this helper compares the item's stored
 * {@code useLevelRequirement} against the player's combat level. The {@code useSkill()} field is read for
 * forward-compatibility but cannot be enforced until TrinityForge exposes per-skill levels.
 * <p>
 * Because it is coarse, the matching toggle ({@code use-level-restriction}) defaults to off. The method
 * fails open: any error, a missing requirement, or TrinityForge being unavailable returns {@code true}
 * (allow), so existing EliteMobs restriction logic is never broken by this hook.
 */
public final class TrinityForgeUseRequirement {

    private TrinityForgeUseRequirement() {
    }

    /**
     * @param player    the player attempting to use/equip the item
     * @param itemStack the item being used
     * @return {@code true} if the player meets the item's TrinityForge use-level requirement (or there is
     * none / the gate is disabled / an error occurred); {@code false} only when a requirement exists and
     * the player's combat level is below it
     */
    public static boolean canUse(Player player, ItemStack itemStack) {
        if (!TrinityForgeIntegration.isUseLevelRestrictionEnabled()) return true;
        if (player == null || itemStack == null) return true;
        try {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) return true;
            ItemData itemData = ItemData.of(meta);
            Optional<Integer> requirement = itemData.useLevelRequirement();
            if (requirement.isEmpty()) return true;
            SymmetricCombatService combatService = TrinityForgeIntegration.combatService();
            if (combatService == null) return true;
            int playerLevel = combatService.combatLevelOf(player.getUniqueId());
            return playerLevel >= requirement.get();
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge use-level check failed (allowing use): " + e.getMessage());
            return true;
        }
    }
}
