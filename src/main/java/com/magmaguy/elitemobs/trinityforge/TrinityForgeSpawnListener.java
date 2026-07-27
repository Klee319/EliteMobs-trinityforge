package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.api.EliteMobSpawnEvent;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.combat.AttackStats;
import com.trinityforge.combat.DefenseStats;
import com.trinityforge.mobs.MobProfile;
import com.trinityforge.pdc.PdcKeys;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Stamps each spawned elite mob's PDC with its TrinityForge defense profile (fork spec section 7).
 * <p>
 * The profile id is the EliteMobs custom-boss config file name, which matches the id produced by
 * TrinityForge's {@code /trinityforge importmobs} converter. TrinityForge reads the stamped PDC at combat time
 * ({@code MobData#hasProfile} is keyed on {@link PdcKeys#MOB_LEVEL}); when no profile is found it falls back to
 * its own {@code combat/mob-defaults.yml}. All numeric values come from TrinityForge's converted profile — this
 * listener only copies them onto the entity.
 */
public class TrinityForgeSpawnListener implements Listener {

    // HIGH (not MONITOR): this listener mutates the entity's PDC, so it must run in a writing phase and leave
    // the completed stamp visible to any HIGHEST/MONITOR observers.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEliteSpawn(EliteMobSpawnEvent event) {
        if (!TrinityForgeIntegration.isSpawnProfileStampEnabled()) return;
        LivingEntity entity = event.getEntity();
        EliteEntity eliteEntity = event.getEliteMobEntity();
        if (entity == null || eliteEntity == null) return;

        String profileId = resolveProfileId(eliteEntity);
        if (profileId == null) return;

        try {
            // Same roll seed as the HP-resolution path (TrinityForgeIntegration#resolveProfileMaxHealth)
            // — read from (and lazily persisted to) this same entity's PDC, so whichever of the two
            // runs first "wins" the seed and the other agrees (fork spec item 2: individual variance).
            long rollSeed = TrinityForgeIntegration.rollSeedFor(entity);
            // Runtime-resolved: a `dynamic` mob (e.g. EliteMobs `level: dynamic`) is rebuilt at this
            // entity's actual spawned level (dynamic dungeon level selection) instead of the baked
            // defaultLevel; fixed mobs come back unchanged. Same bare/".yml" id resolution as HP
            // delegation (TrinityForgeIntegration#resolveProfileMaxHealth). Also overlays TrinityForge's
            // combat/mob-overrides.yml (2026-07-26 ダンジョン×モブ単位オーバーライド新設), keyed on this
            // entity's world so a world-specific override wins over the default-scope one.
            MobProfile profile = TrinityForgeIntegration.lookupRuntimeProfile(profileId, eliteEntity.getLevel(),
                    rollSeed, entity.getWorld().getName());
            if (profile == null) {
                // TrinityForge will use mob-defaults.yml for this mob's stats, but the id still has to be
                // stamped: combat/mob-overrides.yml's per-mob DROP table is resolved at EntityDeathEvent
                // from MOB_PROFILE_ID alone (MobOverrideDropListener), so returning here without it made
                // drop overrides silently impossible for any mob missing from mob-profiles.yml — e.g. a
                // content pack downloaded after the last /trinityforge importmobs run (2026-07-26 fix).
                entity.getPersistentDataContainer()
                        .set(PdcKeys.MOB_PROFILE_ID, PersistentDataType.STRING, profileId);
                return;
            }
            stamp(entity.getPersistentDataContainer(), profile, profileId);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge spawn-profile stamping failed for '" + profileId + "': " + e.getMessage());
        }
    }

    /**
     * The mob id in TrinityForge's vocabulary: the custom-boss file name WITHOUT its extension, which is
     * what {@code /trinityforge importmobs} uses as the {@code combat/mob-profiles.yml} key and what an
     * operator writes in {@code combat/mob-overrides.yml}.
     *
     * <p>MagmaCore's {@code CustomConfigFields} constructor appends {@code .yml} when the name lacks it,
     * so {@code getFilename()} ALWAYS carries the extension. Stripping it here (rather than relying on
     * {@link TrinityForgeIntegration#lookupRuntimeProfile}'s suffix-tolerant retry) keeps the id
     * consistent across all three uses — profile lookup, the {@code MOB_PROFILE_ID} stamp, and the
     * death-time drop-override lookup. Before 2026-07-26 the stamp kept the {@code .yml} form, so
     * {@code MobOverrideDropListener} looked up {@code "boss.yml"} against a config written as
     * {@code boss} and no drop override could ever match.
     */
    private static String resolveProfileId(EliteEntity eliteEntity) {
        if (!(eliteEntity instanceof CustomBossEntity customBossEntity)) {
            return null;
        }
        String filename = customBossEntity.getCustomBossesConfigFields().getFilename();
        return filename == null ? null : stripYamlExtension(filename);
    }

    /**
     * Mirrors {@code ConfigManager#normalizeMobId} / {@code EliteMobsImporter#sanitizeId}: drop the YAML
     * extension, then turn any remaining {@code .} into {@code _} (a dot is a path separator in a YAML
     * config key, so the importer cannot store it verbatim either).
     */
    private static String stripYamlExtension(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        String stripped = filename;
        if (lower.endsWith(".yml")) {
            stripped = filename.substring(0, filename.length() - 4);
        } else if (lower.endsWith(".yaml")) {
            stripped = filename.substring(0, filename.length() - 5);
        }
        return stripped.replace('.', '_');
    }

    private static void stamp(PersistentDataContainer pdc, MobProfile profile, String profileId) {
        DefenseStats physical = profile.physical();
        DefenseStats magical = profile.magical();
        // Validate the whole profile before writing anything: a half-stamped entity (level set but defense
        // missing) is worse for TrinityForge than no profile at all, so we never partially write.
        if (physical == null || magical == null) {
            Logger.warn("TrinityForge profile '" + profile.id() + "' has null defense stats — skipping stamp.");
            return;
        }

        // 2026-07-26 mob-overrides新設: モブidをPDCへ刻む。EntityDeathEvent時点でも
        // combat/mob-overrides.yml のドロップオーバーライドをこのidで解決できるようにするため
        // (MOB_LEVEL等と同じスポーン時刻に、同じ理由で追加された恒久的な識別子)。
        pdc.set(PdcKeys.MOB_PROFILE_ID, PersistentDataType.STRING, profileId);

        pdc.set(PdcKeys.MOB_LEVEL, PersistentDataType.INTEGER, profile.level());

        String theme = profile.dungeonTheme();
        if (theme != null && !theme.isBlank()) {
            pdc.set(PdcKeys.MOB_DUNGEON_THEME, PersistentDataType.STRING, theme);
        }

        pdc.set(PdcKeys.MOB_ARMOR_STRENGTH, PersistentDataType.DOUBLE, profile.armorStrength());

        pdc.set(PdcKeys.MOB_PHYS_DEFENSE_RATE, PersistentDataType.DOUBLE, physical.defenseRate());
        pdc.set(PdcKeys.MOB_PHYS_RESISTANCE, PersistentDataType.DOUBLE, physical.resistance());
        pdc.set(PdcKeys.MOB_PHYS_DAMAGE_REDUCTION, PersistentDataType.DOUBLE, physical.damageReduction());
        pdc.set(PdcKeys.MOB_PHYS_FLAT_DEFENSE, PersistentDataType.DOUBLE, physical.flatDefense());

        pdc.set(PdcKeys.MOB_MAGIC_DEFENSE_RATE, PersistentDataType.DOUBLE, magical.defenseRate());
        pdc.set(PdcKeys.MOB_MAGIC_RESISTANCE, PersistentDataType.DOUBLE, magical.resistance());
        pdc.set(PdcKeys.MOB_MAGIC_DAMAGE_REDUCTION, PersistentDataType.DOUBLE, magical.damageReduction());
        pdc.set(PdcKeys.MOB_MAGIC_FLAT_DEFENSE, PersistentDataType.DOUBLE, magical.flatDefense());

        // Attacker-side stamp (mob-profiles `attack:`): only when the profile carries a configured
        // attack — a MOB_ATTACK_POWER key flips TrinityForge's CombatListener into owning this mob's
        // outgoing melee (physicalFinalDamageFromMob), so an unconfigured profile must NOT write it
        // (the mob keeps EliteMobs' own damage, delegated through TrinityForgeCombatListener instead).
        if (profile.hasAttack()) {
            AttackStats attack = profile.attack();
            pdc.set(PdcKeys.MOB_ATTACK_POWER, PersistentDataType.DOUBLE, attack.defaultDamage());
            pdc.set(PdcKeys.MOB_ATTACK_FLAT_BONUS, PersistentDataType.DOUBLE, attack.flatBonusDamage());
            pdc.set(PdcKeys.MOB_ATTACK_PERCENT_BONUS, PersistentDataType.DOUBLE, attack.percentBonusDamage());
            pdc.set(PdcKeys.MOB_ATTACK_CRIT_CHANCE, PersistentDataType.DOUBLE, attack.critChance());
            pdc.set(PdcKeys.MOB_ATTACK_CRIT_DAMAGE, PersistentDataType.DOUBLE, attack.critDamage());
            pdc.set(PdcKeys.MOB_ATTACK_PENETRATION, PersistentDataType.DOUBLE, attack.penetration());
            pdc.set(PdcKeys.MOB_ATTACK_DAMAGE_MODIFIER, PersistentDataType.DOUBLE, attack.damageModifier());
            pdc.set(PdcKeys.MOB_ATTACK_FIXED_DAMAGE, PersistentDataType.DOUBLE, attack.fixedDamage());
        }
    }
}
