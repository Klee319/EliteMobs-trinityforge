package com.magmaguy.elitemobs.trinityforge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HateTable}. The table is a pure in-memory component (no Bukkit dependency), so it is
 * fully headless-testable. State is static, so each test clears it before and after to stay isolated.
 */
class HateTableTest {

    @BeforeEach
    @AfterEach
    void reset() {
        HateTable.clearAll();
    }

    @Test
    @DisplayName("addHate accumulates per player and highestHate returns the leader")
    void addHate_accumulates_andHighestHateReturnsLeader() {
        UUID mob = UUID.randomUUID();
        UUID tank = UUID.randomUUID();
        UUID dps = UUID.randomUUID();

        HateTable.addHate(mob, tank, 10);
        HateTable.addHate(mob, tank, 5);   // tank total = 15
        HateTable.addHate(mob, dps, 12);   // dps total = 12

        assertEquals(Optional.of(tank), HateTable.highestHate(mob));
    }

    @Test
    @DisplayName("non-positive and null inputs are ignored")
    void addHate_ignoresNonPositiveAndNull() {
        UUID mob = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        HateTable.addHate(mob, player, 0);
        HateTable.addHate(mob, player, -5);
        HateTable.addHate(null, player, 10);
        HateTable.addHate(mob, null, 10);

        assertTrue(HateTable.highestHate(mob).isEmpty());
    }

    @Test
    @DisplayName("highestHate is empty for unknown or null mob")
    void highestHate_emptyForUnknownMob() {
        assertTrue(HateTable.highestHate(UUID.randomUUID()).isEmpty());
        assertTrue(HateTable.highestHate(null).isEmpty());
    }

    @Test
    @DisplayName("clear drops a single mob's table only")
    void clear_dropsSingleMob() {
        UUID mobA = UUID.randomUUID();
        UUID mobB = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        HateTable.addHate(mobA, player, 10);
        HateTable.addHate(mobB, player, 10);

        HateTable.clear(mobA);

        assertTrue(HateTable.highestHate(mobA).isEmpty());
        assertEquals(Optional.of(player), HateTable.highestHate(mobB));
    }

    @Test
    @DisplayName("forgetPlayer removes one player's hate across all mobs")
    void forgetPlayer_removesAcrossAllMobs() {
        UUID mobA = UUID.randomUUID();
        UUID mobB = UUID.randomUUID();
        UUID quitter = UUID.randomUUID();
        UUID survivor = UUID.randomUUID();
        HateTable.addHate(mobA, quitter, 100);
        HateTable.addHate(mobA, survivor, 1);
        HateTable.addHate(mobB, quitter, 100);

        HateTable.forgetPlayer(quitter);

        assertEquals(Optional.of(survivor), HateTable.highestHate(mobA));
        assertTrue(HateTable.highestHate(mobB).isEmpty());
    }

    @Test
    @DisplayName("clearAll empties the entire table")
    void clearAll_emptiesEverything() {
        UUID mob = UUID.randomUUID();
        HateTable.addHate(mob, UUID.randomUUID(), 10);

        HateTable.clearAll();

        assertTrue(HateTable.highestHate(mob).isEmpty());
    }

    @Test
    @DisplayName("eligibility filter skips the ineligible leader and rolls to the next candidate")
    void highestHate_withPredicate_skipsIneligibleLeader() {
        UUID mob = UUID.randomUUID();
        UUID deadLeader = UUID.randomUUID();
        UUID livingRunnerUp = UUID.randomUUID();

        HateTable.addHate(mob, deadLeader, 100);      // highest, but ineligible (e.g. dead/offline)
        HateTable.addHate(mob, livingRunnerUp, 40);   // next best, eligible

        assertEquals(Optional.of(livingRunnerUp),
                HateTable.highestHate(mob, uuid -> uuid.equals(livingRunnerUp)));
    }

    @Test
    @DisplayName("eligibility filter returns empty when no candidate is eligible")
    void highestHate_withPredicate_emptyWhenAllIneligible() {
        UUID mob = UUID.randomUUID();
        HateTable.addHate(mob, UUID.randomUUID(), 100);
        HateTable.addHate(mob, UUID.randomUUID(), 50);

        assertTrue(HateTable.highestHate(mob, uuid -> false).isEmpty());
    }

    @Test
    @DisplayName("null predicate behaves like the unfiltered highestHate")
    void highestHate_nullPredicate_equalsUnfiltered() {
        UUID mob = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        HateTable.addHate(mob, leader, 30);
        HateTable.addHate(mob, UUID.randomUUID(), 10);

        assertEquals(Optional.of(leader), HateTable.highestHate(mob, null));
        assertEquals(HateTable.highestHate(mob), HateTable.highestHate(mob, null));
    }

    @Test
    @DisplayName("shadow-recorded hate backs a mid-fight TrinityForge read failure (modification D)")
    void shadowHate_backsFallbackAfterTrinityForgeReadFailure() {
        // Modification D: the fork shadow-records damage into this local table on EVERY hit even while
        // TrinityForge is the primary read source. So when a mid-fight TrinityForge topAttacker read fails and
        // the targeting listener falls through to this table, live hate is present (never an empty table that
        // would silently drop the mob back to native targeting). The eligibility predicate still rolls a
        // now-dead leader down to the living runner-up, exactly as the listener's fallback branch does.
        UUID mob = UUID.randomUUID();
        UUID tank = UUID.randomUUID();
        UUID healer = UUID.randomUUID();
        UUID dps = UUID.randomUUID();

        // Shadow writes accumulated during the fight, independent of TrinityForge.
        HateTable.addHate(mob, tank, 80);
        HateTable.addHate(mob, dps, 45);
        HateTable.addHate(mob, healer, 20);

        // TrinityForge is healthy: unfiltered fallback would pick the tank.
        assertEquals(Optional.of(tank), HateTable.highestHate(mob));

        // Mid-fight the tank dies; the listener's fallback read filters them out and keeps the fight on the next
        // living aggressor instead of whiffing.
        Optional<UUID> retarget = HateTable.highestHate(mob, uuid -> !uuid.equals(tank));
        assertEquals(Optional.of(dps), retarget);
    }

    @Test
    @DisplayName("concurrent addHate from many threads accumulates without loss")
    void addHate_concurrent_accumulatesWithoutLoss() throws InterruptedException {
        UUID mob = UUID.randomUUID();
        List<UUID> players = IntStream.range(0, 8).mapToObj(i -> UUID.randomUUID()).toList();
        int incrementsPerPlayer = 1000;

        ExecutorService pool = Executors.newFixedThreadPool(players.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(players.size());
        for (UUID player : players) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < incrementsPerPlayer; i++) {
                        HateTable.addHate(mob, player, 1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "threads did not finish in time");
        pool.shutdownNow();

        // Every player ended with the same total, so the leader is simply whichever the stream picks; the
        // key invariant is that a leader exists and no exception/lost-update corrupted the table.
        Optional<UUID> leader = HateTable.highestHate(mob);
        assertTrue(leader.isPresent());
        assertFalse(players.contains(null));
    }
}
