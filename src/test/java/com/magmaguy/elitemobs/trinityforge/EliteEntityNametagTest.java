package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the nametag suppression: it drives the REAL
 * {@link EliteEntity#setNameVisible(boolean)} and asserts what actually lands on the entity.
 * <p>
 * This is the test that dies if the suppression is removed from {@code EliteEntity}: with the guard
 * gone, {@code setNameVisible(true)} writes {@code true} and the first assertion below fails.
 * <p>
 * <b>Why a {@link Proxy} and not MockBukkit.</b> The only Bukkit surface this path touches is
 * {@code LivingEntity#setCustomNameVisible(boolean)}, so a recording proxy covers it exactly. That
 * keeps the fork's test classpath at spigot-api + JUnit (MockBukkit would pull paper-api in beside
 * spigot-api) and, more importantly, removes the failure mode this repository has been bitten by
 * before: MockBukkit turns an unimplemented API into a SKIPPED test, which reads as green.
 */
class EliteEntityNametagTest {

    @AfterEach
    void reset() {
        IntegrationState.reset();
    }

    @Test
    @DisplayName("setNameVisible(true) writes FALSE onto the entity while the nametag is suppressed")
    void suppressedNametagIsForcedOff() throws Exception {
        IntegrationState.set("available", true);
        IntegrationState.set("suppressNametag", true);

        RecordingLivingEntity recorder = new RecordingLivingEntity();
        EliteEntity elite = eliteWith(recorder.proxy());

        elite.setNameVisible(true);

        assertEquals(List.of(false), recorder.writes,
                "EliteMobEnterCombatEvent asks for the nametag; suppression must turn that into a hide");
        assertFalse(recorder.lastWrite());
    }

    @Test
    @DisplayName("setNameVisible(false) still hides, suppression on or off")
    void hideRequestIsNeverInverted() throws Exception {
        IntegrationState.set("available", true);

        IntegrationState.set("suppressNametag", true);
        RecordingLivingEntity suppressed = new RecordingLivingEntity();
        eliteWith(suppressed.proxy()).setNameVisible(false);
        assertFalse(suppressed.lastWrite());

        IntegrationState.set("suppressNametag", false);
        RecordingLivingEntity notSuppressed = new RecordingLivingEntity();
        eliteWith(notSuppressed.proxy()).setNameVisible(false);
        assertFalse(notSuppressed.lastWrite());
    }

    @Test
    @DisplayName("setNameVisible(true) writes TRUE when the nametag is not suppressed")
    void unsuppressedNametagStillShows() throws Exception {
        IntegrationState.set("available", true);
        IntegrationState.set("suppressNametag", false);

        RecordingLivingEntity recorder = new RecordingLivingEntity();
        eliteWith(recorder.proxy()).setNameVisible(true);

        assertTrue(recorder.lastWrite(), "native-display-suppression.nametag: false must leave EliteMobs alone");
    }

    @Test
    @DisplayName("a standalone EliteMobs (no TrinityForge) keeps its nametag")
    void standaloneKeepsNametag() throws Exception {
        IntegrationState.set("available", false);
        IntegrationState.set("suppressNametag", true);

        RecordingLivingEntity recorder = new RecordingLivingEntity();
        eliteWith(recorder.proxy()).setNameVisible(true);

        assertTrue(recorder.lastWrite(), "with nothing else drawing an overhead display EliteMobs must keep its own");
    }

    @Test
    @DisplayName("a dead elite (no living entity) writes nothing at all")
    void deadEliteWritesNothing() {
        IntegrationState.set("available", true);
        IntegrationState.set("suppressNametag", true);
        // No livingEntity assigned: upstream's own null guard must still short-circuit.
        new EliteEntity().setNameVisible(true);
    }

    /** Builds an EliteEntity whose only populated field is the living entity the nametag is written to. */
    private static EliteEntity eliteWith(LivingEntity livingEntity) throws Exception {
        EliteEntity elite = new EliteEntity();
        Field field = EliteEntity.class.getDeclaredField("livingEntity");
        field.setAccessible(true);
        field.set(elite, livingEntity);
        return elite;
    }

    /** Records every {@code setCustomNameVisible} write; every other call returns a type default. */
    private static final class RecordingLivingEntity implements InvocationHandler {

        private final List<Boolean> writes = new ArrayList<>();

        LivingEntity proxy() {
            return (LivingEntity) Proxy.newProxyInstance(
                    LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class}, this);
        }

        boolean lastWrite() {
            assertFalse(writes.isEmpty(), "the production path never wrote the nametag flag at all");
            return writes.get(writes.size() - 1);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setCustomNameVisible":
                    writes.add((Boolean) args[0]);
                    return null;
                case "isCustomNameVisible":
                    return !writes.isEmpty() && writes.get(writes.size() - 1);
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "RecordingLivingEntity" + writes;
                default:
                    return defaultValue(method.getReturnType());
            }
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == void.class) return null;
            if (type == double.class) return 0d;
            if (type == float.class) return 0f;
            if (type == long.class) return 0L;
            if (type == char.class) return (char) 0;
            return 0;
        }
    }
}
