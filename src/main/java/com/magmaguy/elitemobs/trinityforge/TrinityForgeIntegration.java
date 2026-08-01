package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.dungeons.EliteMobsWorld;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.TrinityForge;
import com.trinityforge.combat.SymmetricCombatService;
import com.trinityforge.config.ConfigManager;
import com.trinityforge.hate.HateService;
import com.trinityforge.mobs.MobProfile;
import com.trinityforge.pdc.PdcKeys;
import com.trinityforge.stats.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central access point for the TrinityForge integration in this EliteMobs fork.
 * <p>
 * The fork delegates damage, combat-level, defense-profile, loot-stat and targeting concerns to the
 * TrinityForge plugin. This class detects whether TrinityForge is installed, caches its public service
 * handles and exposes the fork-side feature toggles loaded from {@code trinityforge.yml}.
 * <p>
 * All numeric balance lives in TrinityForge's own configuration (see the fork spec, section 9 — no
 * balance constants are baked into EliteMobs). Dungeon entry rules live exclusively in TrinityForge's
 * {@code dungeon/gates.yml}; EliteMobs has no duplicate gate switch or level table.
 * <p>
 * Every accessor is null/availability guarded: when TrinityForge is absent the fork behaves like vanilla
 * EliteMobs (no neutralization, no delegation) so the plugin still loads in a degraded mode.
 */
public final class TrinityForgeIntegration {

    /** Identity multiplier used when short-circuiting gear-tier scaling (gear-independent combat). */
    public static final double NEUTRAL_GEAR_MULTIPLIER = 1.0;

    private static final String CONFIG_FILE = "trinityforge.yml";
    private static final String TRINITYFORGE_PLUGIN = "TrinityForge";

    private static boolean available = false;
    private static SymmetricCombatService combatService;
    private static ConfigManager config;
    private static ItemFactory itemFactory;
    private static HateService hateService;

    // Feature toggles (defaults applied in loadConfig).
    private static boolean gearNeutralization = true;
    private static boolean combatDelegation = true;
    private static boolean combatLevelMapping = true;
    private static boolean spawnProfileStamp = true;
    private static boolean hpDelegation = true;
    private static boolean lootStatStamp = true;
    private static boolean hateTargeting = true;
    private static boolean repairDisabled = true;
    private static boolean soulbindBridge = true;
    private static boolean useLevelRestriction = false;
    private static boolean suppressNativeCombatDisplay = true;

    // Granular display suppression (2026-08-01). Every key defaults to the
    // suppress-native-combat-display master switch except boss-tracking-bar, which has no TrinityForge
    // counterpart and therefore defaults to "not suppressed" — see trinityforge.yml.
    private static boolean suppressNametag = true;
    private static boolean suppressCustomModelNametag = true;
    private static boolean suppressBossTrackingBar = false;

    // EliteMobs-originated drop sources (2026-08-01). These are the loot paths EliteMobs hands to the
    // player WITHOUT going through TrinityForge's configuration, so they are the ones that show up as
    // "items I never configured are dropping". Defaults documented in trinityforge.yml.
    private static boolean allowRandomEliteLoot = false;
    private static boolean allowSpecialLoot = false;
    private static boolean allowEliteScroll = false;
    private static boolean allowVanillaLootMultiplier = false;
    private static boolean allowVanillaLoot = true;
    private static boolean allowCurrencyShower = true;
    private static boolean allowBossUniqueLoot = true;

    private TrinityForgeIntegration() {
    }

    /**
     * Detects TrinityForge, caches its service handles and loads the fork-side toggles. Safe to call once
     * during {@code onEnable}. If TrinityForge is not present the integration stays disabled.
     *
     * @param plugin the EliteMobs plugin instance (for data folder + scheduling)
     */
    public static void initialize(Plugin plugin) {
        Plugin trinityForge = Bukkit.getPluginManager().getPlugin(TRINITYFORGE_PLUGIN);
        if (trinityForge == null || !trinityForge.isEnabled()) {
            disable();
            Logger.warn("TrinityForge plugin not found — EliteMobs is running in standalone (non-delegated) "
                    + "mode. Combat, loot stats, mob defense profiles and targeting are NOT delegated.");
            return;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                disable();
                Logger.warn("TrinityForge is installed but its instance is not available yet — delegation disabled.");
                return;
            }
            combatService = tf.combatService();
            config = tf.config();
            itemFactory = tf.itemFactory();
            hateService = tf.hateService();
            available = combatService != null && config != null;
            if (available) {
                Logger.info("TrinityForge detected — EliteMobs combat/loot/defense/targeting delegation is active.");
            } else {
                Logger.warn("TrinityForge present but exposed null services — delegation disabled.");
            }
        } catch (RuntimeException | LinkageError e) {
            // Fail open: any runtime failure OR any classloading/binary-incompatibility LinkageError
            // (NoClassDefFoundError, NoSuchMethodError, IncompatibleClassChangeError, ...) from a mismatched
            // TrinityForge must never abort EliteMobs' onEnable — fall back to standalone (non-delegated) mode.
            disable();
            Logger.warn("Failed to bind to TrinityForge services: " + e + " — delegation disabled.");
            return;
        }
        loadConfig(plugin);
        if (available) {
            // Dungeon content-package worlds are created (EliteMobsWorld.create) during EliteMobs'
            // config-loading phase, which runs BEFORE this integration detects TrinityForge (see
            // EliteMobs#syncInitialization ordering). Their register() calls no-op'd at that point
            // because `available` was still false, so replay them here now that TrinityForge is up —
            // otherwise already-loaded dungeon worlds would never be known to TrinityForge's
            // DungeonWorldRegistry until the next create/destroy cycle.
            for (UUID worldId : EliteMobsWorld.getAllWorldUUIDs()) {
                registerDungeonWorld(worldId);
            }
        }
    }

    private static void loadConfig(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.exists()) {
            // Ship a default file from resources so admins can edit toggles without guessing keys.
            plugin.saveResource(CONFIG_FILE, false);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        gearNeutralization = yaml.getBoolean("gear-neutralization", true);
        combatDelegation = yaml.getBoolean("combat-delegation", true);
        combatLevelMapping = yaml.getBoolean("combat-level-mapping", true);
        spawnProfileStamp = yaml.getBoolean("spawn-profile-stamp", true);
        hpDelegation = yaml.getBoolean("hp-delegation", true);
        lootStatStamp = yaml.getBoolean("loot-stat-stamp", true);
        hateTargeting = yaml.getBoolean("hate-targeting", true);
        repairDisabled = yaml.getBoolean("repair-disabled", true);
        soulbindBridge = yaml.getBoolean("soulbind-bridge", true);
        useLevelRestriction = yaml.getBoolean("use-level-restriction", false);
        // Missing key = suppression ON (default true) so an existing server's trinityforge.yml that
        // predates this toggle still gets the duplicate-display fix without an admin edit.
        suppressNativeCombatDisplay = yaml.getBoolean("suppress-native-combat-display", true);
        // Granular overrides: a missing key inherits the master switch, so an existing server's file that
        // predates this section behaves exactly as the master says (and gets the nametag fix for free).
        suppressNametag = yaml.getBoolean("native-display-suppression.nametag", suppressNativeCombatDisplay);
        suppressCustomModelNametag = yaml.getBoolean("native-display-suppression.custom-model-nametag",
                suppressNativeCombatDisplay);
        // No TrinityForge counterpart (it shows distance/direction, not HP) — never suppressed unless asked.
        suppressBossTrackingBar = yaml.getBoolean("native-display-suppression.boss-tracking-bar", false);

        allowRandomEliteLoot = yaml.getBoolean("elite-drop-sources.random-loot", false);
        allowSpecialLoot = yaml.getBoolean("elite-drop-sources.special-loot", false);
        allowEliteScroll = yaml.getBoolean("elite-drop-sources.elite-scroll", false);
        allowVanillaLootMultiplier = yaml.getBoolean("elite-drop-sources.vanilla-loot-multiplier", false);
        allowVanillaLoot = yaml.getBoolean("elite-drop-sources.vanilla-loot", true);
        allowCurrencyShower = yaml.getBoolean("elite-drop-sources.currency-shower", true);
        allowBossUniqueLoot = yaml.getBoolean("elite-drop-sources.boss-unique-loot", true);
        logSuppressedDropSources();
    }

    /**
     * Logs, once at startup, exactly which EliteMobs-originated drop sources are currently blocked.
     * Silently deleting loot is the failure mode this feature is most likely to be blamed for, so the
     * blocked set is always announced (fork spec 2026-08-01 "無言で全部消すのは避ける").
     */
    private static void logSuppressedDropSources() {
        if (!available) return;
        java.util.List<String> blocked = new java.util.ArrayList<>();
        if (!allowRandomEliteLoot) blocked.add("random-loot (procedural/weighed/fixed/limited/scalable)");
        if (!allowSpecialLoot) blocked.add("special-loot");
        if (!allowEliteScroll) blocked.add("elite-scroll");
        if (!allowVanillaLootMultiplier) blocked.add("vanilla-loot-multiplier");
        if (!allowVanillaLoot) blocked.add("vanilla-loot");
        if (!allowCurrencyShower) blocked.add("currency-shower");
        if (!allowBossUniqueLoot) blocked.add("boss-unique-loot");
        if (blocked.isEmpty()) {
            Logger.info("TrinityForge: every EliteMobs drop source is enabled (elite-drop-sources).");
            return;
        }
        Logger.info("TrinityForge: EliteMobs-originated drop sources blocked by elite-drop-sources in "
                + CONFIG_FILE + ": " + String.join(", ", blocked)
                + ". TrinityForge's own combat/mob-overrides.yml drops are unaffected.");
    }

    /** Marks the integration unavailable and drops any cached service references so a stale TrinityForge
     * classloader can be garbage-collected after a reload where TrinityForge was removed. */
    private static void disable() {
        available = false;
        combatService = null;
        config = null;
        itemFactory = null;
        hateService = null;
    }

    /**
     * Public shutdown hook invoked from {@code EliteMobs#onDisable}. Releases the cached TrinityForge
     * service handles and marks the integration unavailable so nothing holds a stale TrinityForge
     * classloader after the plugin stops (or after a reload where TrinityForge was removed).
     */
    public static void shutdown() {
        disable();
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Pushes a dungeon-instance world's UUID into TrinityForge's {@code DungeonWorldRegistry} so
     * TrinityForge can recognize the world as a dungeon (e.g. for the combat-level entry gate).
     * No-op (never throws) when TrinityForge is unavailable — fork spec item 1. Called centrally from
     * {@link EliteMobsWorld#create} / the plugin-startup replay in {@link #initialize}.
     */
    public static void registerDungeonWorld(UUID worldId) {
        if (!available || worldId == null) return;
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.dungeonWorldRegistry() == null) return;
            tf.dungeonWorldRegistry().register(worldId);
        } catch (RuntimeException | LinkageError e) {
            Logger.warn("TrinityForge dungeon-world registration failed for " + worldId + ": " + e.getMessage());
        }
    }

    /**
     * Removes a dungeon-instance world's UUID from TrinityForge's {@code DungeonWorldRegistry} when
     * the instance is torn down. No-op (never throws) when TrinityForge is unavailable — fork spec
     * item 1. Called centrally from {@link EliteMobsWorld#destroy}.
     */
    public static void unregisterDungeonWorld(UUID worldId) {
        if (!available || worldId == null) return;
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.dungeonWorldRegistry() == null) return;
            tf.dungeonWorldRegistry().unregister(worldId);
        } catch (RuntimeException | LinkageError e) {
            Logger.warn("TrinityForge dungeon-world unregistration failed for " + worldId + ": " + e.getMessage());
        }
    }

    /**
     * Applies TrinityForge's death durability penalty ({@code combat/damage.yml durability.on-death}).
     *
     * <p>2026-07-30: instanced dungeons cancel the lethal damage in
     * {@code MatchInstance.MatchInstanceEvents.onPlayerDamage} and route the player into the "downed"
     * state, so {@code PlayerDeathEvent} never fires inside a dungeon and TrinityForge's own death
     * penalty never ran. EliteMobs' own
     * {@code com.magmaguy.elitemobs.collateralminecraftchanges.AlternativeDurabilityLoss} only touches
     * EliteMobs-tagged items, so TrinityForge gear came out of a dungeon death untouched.
     * Called from {@code InstancePlayerManager#playerDeath} <em>before</em> the player is moved to
     * spectator mode (TrinityForge skips creative/spectator players).
     *
     * <p>No-op (never throws) when TrinityForge is unavailable, and the dungeon-only / game-mode
     * gating lives on the TrinityForge side, so this call is unconditional by design.
     */
    public static void applyDeathDurabilityPenalty(org.bukkit.entity.Player player) {
        if (!available || player == null) return;
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) return;
            tf.applyDeathDurabilityPenalty(player);
        } catch (RuntimeException | LinkageError e) {
            Logger.warn("TrinityForge death durability penalty failed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Resolves (and lazily persists) a stable per-mob roll seed used to scale HP and attack power
     * together (fork spec item 2 — individual variance). Reads {@link PdcKeys#MOB_ROLL_SEED} from the
     * entity's PDC; if absent, rolls one and writes it back so the HP-resolution path
     * ({@link #resolveProfileMaxHealth}) and the defense/attack-stamp path
     * ({@code TrinityForgeSpawnListener#stamp}) agree on the same seed — and therefore the same
     * variance multiplier — no matter which one runs first for a given mob.
     *
     * @param entity the spawned Bukkit entity (its PDC is the seed's persistence backing); {@code null}
     *               is tolerated and yields {@code 0L} (identity/no-variance) so callers stay null-safe.
     */
    public static long rollSeedFor(Entity entity) {
        if (entity == null) return 0L;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Long existing = pdc.get(PdcKeys.MOB_ROLL_SEED, PersistentDataType.LONG);
        if (existing != null) return existing;
        long seed = ThreadLocalRandom.current().nextLong();
        pdc.set(PdcKeys.MOB_ROLL_SEED, PersistentDataType.LONG, seed);
        return seed;
    }

    /**
     * @return true when EliteMobs' own native combat displays (health bars, numeric HP, boss bars,
     * damage/heal/XP popups, and the vanilla nametag) should be suppressed because TrinityForge's own
     * FocusHp/DamagePopup systems already show equivalent information (fork spec item 3). Gated on
     * {@link #available} like every other integration toggle: with TrinityForge absent there is nothing
     * to avoid duplicating, so EliteMobs keeps its native displays in standalone mode.
     */
    public static boolean isSuppressNativeCombatDisplayEnabled() {
        return available && suppressNativeCombatDisplay;
    }

    /**
     * @return true when EliteMobs' vanilla overhead nametag (the {@code customNameVisible} flag on the
     * mob itself, including the {@code alwaysShowName} custom-boss override and the LibsDisguises mirror)
     * should stay hidden because TrinityForge's FocusHp display already shows name + level. Gated on
     * {@link #available}: with TrinityForge absent EliteMobs keeps its own nametags.
     */
    public static boolean isSuppressNametagEnabled() {
        return available && suppressNametag;
    }

    /**
     * @return true when the nametag rendered by a custom model (FreeMinecraftModels / ModelEngine
     * nametag bone) should stay hidden. Separate from {@link #isSuppressNametagEnabled()} because a
     * modeled boss' nametag is drawn by the model plugin at a different height and is the one most
     * likely to visually collide with TrinityForge's own overhead display.
     */
    public static boolean isSuppressCustomModelNametagEnabled() {
        return available && suppressCustomModelNametag;
    }

    /**
     * @return true when EliteMobs' boss tracking boss-bar ("$name: $distance blocks away!") should not be
     * created. Defaults to false — it shows distance/direction, which TrinityForge has no equivalent for,
     * so it is NOT a duplicate display; it only competes for boss-bar screen space.
     */
    public static boolean isSuppressBossTrackingBarEnabled() {
        return available && suppressBossTrackingBar;
    }

    /**
     * Whether an EliteMobs-originated drop source may run. Every one of these fails OPEN when
     * TrinityForge is absent: a standalone EliteMobs install must behave exactly like upstream.
     */
    private static boolean dropSourceAllowed(boolean flag) {
        return !available || flag;
    }

    /** EliteMobs' random elite loot pool (procedural / weighed / fixed / limited / scalable). */
    public static boolean isRandomEliteLootAllowed() {
        return dropSourceAllowed(allowRandomEliteLoot);
    }

    /** EliteMobs' SpecialItemSystems bonus drop. */
    public static boolean isSpecialLootAllowed() {
        return dropSourceAllowed(allowSpecialLoot);
    }

    /** EliteMobs' elite item scroll drop ({@code ItemSettings.yml useEliteItemScrolls}). */
    public static boolean isEliteScrollAllowed() {
        return dropSourceAllowed(allowEliteScroll);
    }

    /** EliteMobs' vanilla-drop duplication ({@code ItemSettings.yml defaultLootMultiplier}). */
    public static boolean isVanillaLootMultiplierAllowed() {
        return dropSourceAllowed(allowVanillaLootMultiplier);
    }

    /** The vanilla death drops themselves, for mobs configured with {@code dropsVanillaLoot: true}. */
    public static boolean isVanillaLootAllowed() {
        return dropSourceAllowed(allowVanillaLoot);
    }

    /** EliteMobs' guild-currency ({@code EliteCoin}) loot shower. */
    public static boolean isCurrencyShowerAllowed() {
        return dropSourceAllowed(allowCurrencyShower);
    }

    /** A custom boss' own authored {@code uniqueLootList} (custombosses/*.yml). */
    public static boolean isBossUniqueLootAllowed() {
        return dropSourceAllowed(allowBossUniqueLoot);
    }

    public static SymmetricCombatService combatService() {
        return combatService;
    }

    public static ConfigManager config() {
        return config;
    }

    public static ItemFactory itemFactory() {
        return itemFactory;
    }

    /**
     * @return TrinityForge's shared hate/threat service (cap/TTL/decay/deterministic tie-break),
     * or {@code null} when TrinityForge is absent or has not exposed it. Cached at
     * {@link #initialize} in the same manner as {@link #combatService()} / {@link #config()}. The
     * hate-targeting listener null-guards this and falls back to the fork's local
     * {@code HateTable} when it is null (degraded standalone).
     */
    public static HateService hateService() {
        return hateService;
    }

    public static boolean isGearNeutralizationEnabled() {
        return available && gearNeutralization;
    }

    public static boolean isCombatDelegationEnabled() {
        return available && combatDelegation;
    }

    public static boolean isCombatLevelMappingEnabled() {
        return available && combatLevelMapping;
    }

    public static boolean isSpawnProfileStampEnabled() {
        return available && spawnProfileStamp;
    }

    public static boolean isHpDelegationEnabled() {
        return available && hpDelegation;
    }

    /**
     * Resolves the TrinityForge-driven max health for {@code eliteEntity}, or {@code 0} when the mob's
     * HP should stay EliteMobs-driven (the integration is off, the mob is not a custom boss, no
     * TrinityForge profile exists for it, or its profile has no configured {@code max-health}).
     * <p>
     * Called from {@link EliteEntity#setMaxHealth()} so EVERY EliteMobs HP write (initial spawn,
     * full-heal, phase-boss reset) uses the same TrinityForge value and stays consistent, rather than
     * having a spawn listener fight EliteMobs' own later re-heals. The profile id is the custom-boss
     * config file name — the same id {@code /trinityforge importmobs} keys profiles on and
     * {@link TrinityForgeSpawnListener} stamps. Fails open (returns 0) on any error so a mismatched
     * TrinityForge can never break EliteMobs' HP.
     */
    public static double resolveProfileMaxHealth(EliteEntity eliteEntity) {
        if (!isHpDelegationEnabled() || eliteEntity == null) return 0.0;
        if (!(eliteEntity instanceof CustomBossEntity customBossEntity)) return 0.0;
        try {
            String profileId = customBossEntity.getCustomBossesConfigFields().getFilename();
            if (profileId == null) return 0.0;
            // Same roll seed as the defense/attack stamp (TrinityForgeSpawnListener#stamp) — read from
            // (and lazily persisted to) the entity's own PDC, so whichever of the two runs first for
            // this mob "wins" the seed and the other agrees, keeping HP and attack scaled by the same
            // per-mob variance multiplier (fork spec item 2).
            long rollSeed = rollSeedFor(eliteEntity.getLivingEntity());
            // Runtime-resolved: for a `dynamic` mob this rebuilds the profile at the mob's actual
            // spawned level (dynamic dungeon level selection) instead of the baked defaultLevel;
            // fixed mobs come back unchanged. Every HP write (spawn, full-heal, phase reset) goes
            // through EliteEntity#setMaxHealth(), which calls this, so they all stay consistent.
            // World-scoped (2026-07-26 mob-overrides新設) so combat/mob-overrides.yml's max-health
            // override (if any) is honoured on every HP re-assert, not just at spawn.
            String worldName = eliteEntity.getLivingEntity() == null ? null
                    : eliteEntity.getLivingEntity().getWorld().getName();
            MobProfile profile = lookupRuntimeProfile(profileId, eliteEntity.getLevel(), rollSeed, worldName);
            if (profile == null || !profile.hasMaxHealth()) return 0.0;
            return profile.maxHealth();
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge HP delegation lookup failed: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * TrinityForge keys profiles on the custom-boss file name; tolerate either a bare or ".yml"-suffixed
     * id, mirroring {@link TrinityForgeSpawnListener}'s lookup so spawn-stamp and HP delegation agree on
     * the same profile. Runtime-level-and-seed-aware wrapper around
     * {@link ConfigManager#resolveRuntimeProfile(String, int, long)} (which itself does not do
     * bare/".yml" suffix matching — that resolution stays here, same as the pre-existing
     * {@code lookupProfile}). {@code rollSeed} must come from {@link #rollSeedFor} for the SAME entity
     * as {@code runtimeLevel} so HP and attack/defense scale by the same per-mob variance multiplier.
     * Returns {@code null} when no profile matches.
     */
    static MobProfile lookupRuntimeProfile(String profileId, int runtimeLevel, long rollSeed) {
        return lookupRuntimeProfile(profileId, runtimeLevel, rollSeed, null);
    }

    /**
     * World-scoped variant (2026-07-26 mob-overrides新設): additionally overlays
     * {@code combat/mob-overrides.yml}'s world-specific scope (if any) on top of the {@code default}
     * scope, via {@link ConfigManager#resolveRuntimeProfile(String, int, long, String)}. {@code
     * worldName} may be {@code null} (only the {@code default} scope, if any, applies — same as the
     * 3-arg {@link #lookupRuntimeProfile(String, int, long)}).
     */
    static MobProfile lookupRuntimeProfile(String profileId, int runtimeLevel, long rollSeed, String worldName) {
        Optional<MobProfile> direct = config.resolveRuntimeProfile(profileId, runtimeLevel, rollSeed, worldName);
        if (direct.isPresent()) return direct.get();
        if (profileId.toLowerCase().endsWith(".yml")) {
            return config.resolveRuntimeProfile(profileId.substring(0, profileId.length() - 4), runtimeLevel,
                            rollSeed, worldName)
                    .orElse(null);
        }
        return null;
    }

    public static boolean isLootStatStampEnabled() {
        return available && lootStatStamp;
    }

    public static boolean isHateTargetingEnabled() {
        return available && hateTargeting;
    }

    /**
     * @return true when EliteMobs item repair (custom scrap repair + vanilla anvil/mending) should be
     * blocked so durability behaves as a finite resource (fork spec section 8). Off unless TrinityForge
     * is available.
     */
    public static boolean isRepairDisabled() {
        return available && repairDisabled;
    }

    /**
     * @return true when EliteMobs soulbinds should also be mirrored into TrinityForge {@code ItemData}
     * (bind type + owner) so TrinityForge observes the same binding (fork spec section 8).
     */
    public static boolean isSoulbindBridgeEnabled() {
        return available && soulbindBridge;
    }

    /**
     * @return true when item {@code useLevelRequirement} stored in TrinityForge {@code ItemData} should
     * gate equipping against the player's combat level (fork spec section 8). Default off — this is a
     * coarse placeholder until TrinityForge exposes per-skill ValhallaMMO levels.
     */
    public static boolean isUseLevelRestrictionEnabled() {
        return available && useLevelRestriction;
    }

}
