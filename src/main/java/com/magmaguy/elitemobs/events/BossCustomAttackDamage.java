package com.magmaguy.elitemobs.events;

import com.magmaguy.elitemobs.api.PlayerDamagedByEliteMobEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class BossCustomAttackDamage {

    public static double dealCustomDamage(LivingEntity damager, LivingEntity damagee, double damage) {

        if (damager == null || damagee == null) return 0;
        if (damager.equals(damagee)) return 0;
        if (damagee.isInvulnerable() || damagee.getHealth() <= 0) return 0;

        if (damagee instanceof Player)
            if (!(((Player) damagee).getGameMode().equals(GameMode.SURVIVAL) ||
                    ((Player) damagee).getGameMode().equals(GameMode.ADVENTURE))) return 0;

        PlayerDamagedByEliteMobEvent.PlayerDamagedByEliteMobEventFilter.setBypass(true);
        if (damagee instanceof Player) {
            // Custom power damage against a player is elite ABILITY damage: mark so the TrinityForge
            // combat listener routes it through the MAGICAL component instead of treating the
            // synthetic damage call as a melee hit.
            com.magmaguy.elitemobs.trinityforge.TrinityForgeAbilityDamage.mark();
            try {
                damagee.damage(damage, damager);
            } finally {
                com.magmaguy.elitemobs.trinityforge.TrinityForgeAbilityDamage.clear();
            }
        } else {
            damagee.damage(damage, damager);
        }
        damagee.setNoDamageTicks(0);

        return damage;
    }

}
