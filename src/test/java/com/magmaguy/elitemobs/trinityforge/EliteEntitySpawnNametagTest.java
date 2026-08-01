package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.config.DefaultConfig;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.elitemobs.mobconstructor.mobdata.aggressivemobs.EliteMobProperties;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * HIGH-1 (2026-08-01): drives the REAL {@link EliteEntity#setName(EliteMobProperties)} — the spawn-time
 * naming path, distinct from {@link EliteEntity#setNameVisible(boolean)} which
 * {@link EliteEntityNametagTest} already covers — and asserts what actually lands on the entity.
 * <p>
 * A 2026-08-01 mutation flipped the second argument of
 * {@code NativeDisplayPolicy.resolveSpawnNametagVisible(alwaysShowNametags, false)} from {@code false}
 * to {@code true} inside {@code setName}. {@link NativeDisplayPolicyTest} pins what
 * {@code resolveSpawnNametagVisible} RETURNS for a given pair of booleans, and
 * {@link TrinityForgeGateWiringTest} pins that {@code EliteEntity} still calls it at all — neither pins
 * which literal is passed at this call site, so the mutation left both green. This test drives
 * {@code setName} itself and fails only when that second argument stops being {@code false}: with
 * {@code alwaysShowNametags=false} the correct call resolves to "hidden" while the mutated call
 * (unconditionally requesting "always show") resolves to "visible".
 */
class EliteEntitySpawnNametagTest {

    @AfterEach
    void reset() throws ReflectiveOperationException {
        IntegrationState.reset();
        setAlwaysShowNametags(false);
    }

    @Test
    @DisplayName("setName(EliteMobProperties) hides the spawn nametag when nothing asks to always show it")
    void spawnNametagHiddenByDefault() throws Exception {
        setAlwaysShowNametags(false);
        IntegrationState.set("available", true);
        IntegrationState.set("suppressNametag", false); // isolate this call site from the suppression gate

        RecordingLivingEntity recorder = new RecordingLivingEntity();
        EliteEntity elite = eliteWith(recorder.proxy());

        elite.setName(newEliteMobProperties("TestBoss"));

        assertEquals(List.of(false), recorder.writes,
                "setName must resolve resolveSpawnNametagVisible(alwaysShowNametags=false, false) to "
                        + "false. If this now reads true, the second argument at the setName call site "
                        + "regressed from false back to true (HIGH-1, 2026-08-01).");
        assertEquals("TestBoss", recorder.lastName());
    }

    @Test
    @DisplayName("setName(EliteMobProperties) still shows the spawn nametag when DefaultConfig says always-show")
    void spawnNametagShownWhenConfiguredAlwaysOn() throws Exception {
        setAlwaysShowNametags(true);
        IntegrationState.set("available", true);
        IntegrationState.set("suppressNametag", false);

        RecordingLivingEntity recorder = new RecordingLivingEntity();
        EliteEntity elite = eliteWith(recorder.proxy());

        elite.setName(newEliteMobProperties("TestBoss"));

        assertFalse(recorder.writes.isEmpty());
        assertEquals(true, recorder.writes.get(recorder.writes.size() - 1),
                "with DefaultConfig.alwaysShowNametags=true the nametag must still show");
    }

    private static void setAlwaysShowNametags(boolean value) throws ReflectiveOperationException {
        Field field = DefaultConfig.class.getDeclaredField("alwaysShowNametags");
        field.setAccessible(true);
        field.set(null, value);
    }

    /** Builds an EliteEntity whose only populated field is the living entity the nametag is written to. */
    private static EliteEntity eliteWith(LivingEntity livingEntity) throws Exception {
        EliteEntity elite = new EliteEntity();
        Field field = EliteEntity.class.getDeclaredField("livingEntity");
        field.setAccessible(true);
        field.set(elite, livingEntity);
        return elite;
    }

    /**
     * Builds a minimal {@link EliteMobProperties} without running its real constructor (which clones
     * static power sets that expect config to already be loaded) — {@code Unsafe.allocateInstance} skips
     * all field initializers and constructors entirely, then the plain public {@code name} field is set
     * directly.
     */
    private static EliteMobProperties newEliteMobProperties(String name) throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        TestEliteMobProperties properties =
                (TestEliteMobProperties) unsafe.allocateInstance(TestEliteMobProperties.class);
        properties.name = name;
        return properties;
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    private static final class TestEliteMobProperties extends EliteMobProperties {
    }

    /** Records every {@code setCustomName}/{@code setCustomNameVisible} write. */
    private static final class RecordingLivingEntity implements InvocationHandler {

        private final List<Boolean> writes = new ArrayList<>();
        private String lastName;

        LivingEntity proxy() {
            return (LivingEntity) Proxy.newProxyInstance(
                    LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class}, this);
        }

        String lastName() {
            return lastName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setCustomName":
                    lastName = (String) args[0];
                    return null;
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
