package com.magmaguy.elitemobs.trinityforge;

/**
 * Every decision that {@code trinityforge.yml}'s {@code native-display-suppression} section makes, in
 * one place, expressed as small deterministic functions.
 * <p>
 * Same rationale as {@link EliteDropPolicy}: the suppression used to be inline boolean expressions in
 * {@code EliteEntity}, {@code CustomBossEntity} and {@code CustomBossMegaConsumer}, none of which a unit
 * test could reach, so deleting the suppression left every test green (verified by mutation on
 * 2026-08-01). {@code NativeDisplayPolicyTest} fixes the decisions, {@code EliteEntityNametagTest}
 * drives the real {@code EliteEntity#setNameVisible} end to end, and {@code TrinityForgeGateWiringTest}
 * pins the fact that the production classes still call in here.
 * <p>
 * Direction of the gate is the OPPOSITE of {@link EliteDropPolicy}: suppression fails CLOSED when
 * TrinityForge is absent, because with nothing else drawing an overhead display EliteMobs must keep its
 * own (see {@link TrinityForgeIntegration#isSuppressNametagEnabled()}).
 */
public final class NativeDisplayPolicy {

    private NativeDisplayPolicy() {
    }

    /**
     * Resolves the vanilla nametag visibility ({@code customNameVisible}) actually written to an elite.
     * <p>
     * Suppression can only ever HIDE, never force-show: a {@code false} request stays {@code false}.
     * This is the guard on {@code EliteEntity#setNameVisible}, whose caller
     * {@code EliteMobEnterCombatEvent} turns the nametag back ON for every elite that enters combat and
     * therefore used to undo what {@code setName} had just hidden.
     *
     * @param requestedVisible what EliteMobs asked for
     */
    public static boolean resolveNametagVisible(boolean requestedVisible) {
        return requestedVisible && !TrinityForgeIntegration.isSuppressNametagEnabled();
    }

    /**
     * Resolves a custom boss' effective nametag visibility, applying the boss'
     * {@code alwaysShowName: true} override.
     * <p>
     * {@code alwaysShowName} is the reason custom bosses kept their nametag even after
     * {@code EliteEntity#setName} hid it, so the override itself has to be suppressed — otherwise it
     * re-enables the very thing being suppressed.
     *
     * @param requestedVisible what EliteMobs asked for
     * @param alwaysShowName   the boss' {@code alwaysShowName} config field
     */
    public static boolean resolveCustomBossNametagVisible(boolean requestedVisible, boolean alwaysShowName) {
        return resolveNametagVisible(requestedVisible || alwaysShowName);
    }

    /**
     * Resolves the visibility of the nametag drawn by a custom model (FreeMinecraftModels /
     * ModelEngine nametag bone). Separate switch from the vanilla nametag because the modeled nametag is
     * drawn at the bone height, which is exactly where TrinityForge's FocusHp display sits.
     *
     * @param requestedVisible the visibility EliteMobs resolved for the entity itself
     */
    public static boolean resolveCustomModelNametagVisible(boolean requestedVisible) {
        return requestedVisible && !TrinityForgeIntegration.isSuppressCustomModelNametagEnabled();
    }

    /**
     * Resolves the nametag visibility applied on the CUSTOM BOSS SPAWN path
     * ({@code CustomBossMegaConsumer#setName}). Same rule as
     * {@link #resolveCustomBossNametagVisible(boolean, boolean)} with EliteMobs' global
     * {@code alwaysShowNametags} folded in — this path never receives a per-call request.
     *
     * @param alwaysShowNametags EliteMobs' global {@code DefaultConfig alwaysShowNametags}
     * @param alwaysShowName     the boss' own {@code alwaysShowName} config field
     */
    public static boolean resolveSpawnNametagVisible(boolean alwaysShowNametags, boolean alwaysShowName) {
        return resolveNametagVisible(alwaysShowNametags || alwaysShowName);
    }

    /**
     * @return true when EliteMobs' own combat-level text display above PLAYERS
     * ({@code CombatLevelDisplay}) may be created.
     * <p>
     * 2026-08-03: this was the one overhead display the master switch never reached. It mounts a
     * packet {@code FakeText} on the player at {@code y + 0.5} — the exact band TrinityForge draws the
     * equipped-title line in — so with both on, the two texts sit on top of each other. It was only
     * invisible in practice because {@code SkillsConfig.showCombatLevelDisplay} defaults to false;
     * turning that on would have silently reintroduced the overlap with no way to stop it from the
     * TrinityForge side. Gated on the master switch like every other duplicate display.
     */
    public static boolean allowPlayerCombatLevelDisplay() {
        return !TrinityForgeIntegration.isSuppressNativeCombatDisplayEnabled();
    }

    /**
     * @return true when the boss tracking boss-bar ("$name: $distance blocks away!") may be created. It
     * shows distance/direction, which TrinityForge has no equivalent for, so it is NOT a duplicate
     * display and defaults to allowed; suppressing it is opt-in for servers that want the boss-bar row
     * free for TrinityForge's own bars.
     */
    public static boolean allowBossTrackingBar() {
        return !TrinityForgeIntegration.isSuppressBossTrackingBarEnabled();
    }
}
