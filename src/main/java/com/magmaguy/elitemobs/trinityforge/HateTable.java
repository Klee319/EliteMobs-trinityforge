package com.magmaguy.elitemobs.trinityforge;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * In-memory hate (threat) table shared by the targeting system (fork spec section 4).
 * <p>
 * Maps each elite mob (by entity UUID) to the accumulated hate each player has generated against it. The
 * targeting listener reads the highest-hate player to redirect the mob's target. Tanks raise hate by dealing
 * damage; pets/beastmasters would contribute to the same table (the fork keeps a single source of truth here so
 * TrinityForge can later absorb a pure HateTable component without changing the contract).
 * <p>
 * This is the pure component the fork owns; the actual per-action hate rates are a balance concern and are passed
 * in by the caller rather than baked here.
 */
public final class HateTable {

    private static final Map<UUID, Map<UUID, Double>> HATE = new ConcurrentHashMap<>();

    private HateTable() {
    }

    /**
     * Adds hate from a player against a mob. Negative or zero amounts are ignored.
     *
     * @param mobUuid    the elite mob's entity UUID
     * @param playerUuid the player's UUID
     * @param amount     hate to add (caller-supplied; the rate is not baked into this table)
     */
    public static void addHate(UUID mobUuid, UUID playerUuid, double amount) {
        if (mobUuid == null || playerUuid == null || amount <= 0) return;
        HATE.computeIfAbsent(mobUuid, k -> new ConcurrentHashMap<>())
                .merge(playerUuid, amount, Double::sum);
    }

    /**
     * @param mobUuid the elite mob's entity UUID
     * @return the player UUID with the highest hate against this mob, if any
     */
    public static Optional<UUID> highestHate(UUID mobUuid) {
        return highestHate(mobUuid, null);
    }

    /**
     * Returns the highest-hate player against a mob whose UUID also passes the {@code eligible} predicate,
     * skipping ineligible leaders and rolling down to the next candidate. Callers use the predicate to exclude
     * dead/offline/cross-world players so the mob retargets to the next-best living aggressor instead of
     * whiffing on an unreachable top-hate slot (fork spec section 4). Keeping the eligibility check in a
     * caller-supplied predicate leaves this table a pure, Bukkit-free component (headless-testable); the
     * targeting listener supplies the Bukkit-aware predicate.
     *
     * @param mobUuid  the elite mob's entity UUID
     * @param eligible predicate a candidate player UUID must pass to be considered; {@code null} means no
     *                 filtering (equivalent to {@link #highestHate(UUID)})
     * @return the highest-hate eligible player UUID, if any
     */
    public static Optional<UUID> highestHate(UUID mobUuid, Predicate<UUID> eligible) {
        if (mobUuid == null) return Optional.empty();
        Map<UUID, Double> table = HATE.get(mobUuid);
        if (table == null || table.isEmpty()) return Optional.empty();
        return table.entrySet().stream()
                .filter(entry -> eligible == null || eligible.test(entry.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /** Drops all hate tracked for a mob (e.g. when it leaves combat or is removed). */
    public static void clear(UUID mobUuid) {
        if (mobUuid != null) HATE.remove(mobUuid);
    }

    /** Drops a single player's hate contribution across all mobs (e.g. on quit). */
    public static void forgetPlayer(UUID playerUuid) {
        if (playerUuid == null) return;
        for (Map<UUID, Double> table : HATE.values()) {
            table.remove(playerUuid);
        }
    }

    /** Drops the entire table (e.g. on plugin disable). */
    public static void clearAll() {
        HATE.clear();
    }
}
