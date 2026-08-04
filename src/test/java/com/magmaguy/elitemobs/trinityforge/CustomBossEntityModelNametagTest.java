package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.config.custombosses.CustomBossesConfigFields;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import com.magmaguy.elitemobs.thirdparty.custommodels.CustomModelInterface;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * HIGH-1 (2026-08-01): drives the REAL {@link CustomBossEntity#setNameVisible(boolean)} and asserts
 * what actually lands on the custom model's nametag.
 * <p>
 * A 2026-08-01 mutation changed the last line of {@code setNameVisible} from passing
 * {@code requestedVisibility} to passing {@code effectiveVisibility} into
 * {@code NativeDisplayPolicy.resolveCustomModelNametagVisible(...)}. {@link NativeDisplayPolicyTest}
 * pins what that method RETURNS for a given input, and {@link TrinityForgeGateWiringTest} pins that
 * {@code CustomBossEntity} still calls it — neither pins WHICH of the two locals is passed, so the
 * mutation left both green. This test picks a scenario where {@code requestedVisibility} and
 * {@code effectiveVisibility} actually diverge (a boss with {@code alwaysShowName: true}, vanilla
 * nametag suppressed, model nametag NOT suppressed, and a hide request) and fails only when the wrong
 * one is used.
 */
class CustomBossEntityModelNametagTest {

    @BeforeEach
    void installServer() {
        FakeBukkitServer.install();
    }

    @AfterEach
    void reset() {
        IntegrationState.reset();
    }

    @Test
    @DisplayName("the custom model's nametag follows the REQUESTED visibility, not the vanilla-suppressed one")
    void modelNametagUsesRequestedVisibilityNotEffectiveVisibility() throws Exception {
        IntegrationState.set("available", true);
        IntegrationState.set("suppressNametag", true); // forces effectiveVisibility down
        IntegrationState.set("suppressCustomModelNametag", false); // must NOT drag the model tag with it

        RecordingLivingEntity livingEntity = new RecordingLivingEntity();
        RecordingCustomModel model = new RecordingCustomModel();
        CustomBossEntity boss = bossWith(livingEntity.proxy(), model.proxy(), true /* alwaysShowName */);

        // isVisible=false + alwaysShowName=true => requestedVisibility=true, but vanilla suppression
        // forces effectiveVisibility=false. The two now disagree, which is the whole point.
        boss.setNameVisible(false);

        assertEquals(List.of(true), model.nameVisibleWrites,
                "customModel.setNameVisible must be called with requestedVisibility=true here. If this "
                        + "reads [false], the call site regressed to passing effectiveVisibility instead "
                        + "(HIGH-1, 2026-08-01) and native-display-suppression.nametag would incorrectly "
                        + "drag down native-display-suppression.custom-model-nametag too.");
    }

    /**
     * 2026-08-04: {@code CustomBossEntity#setName(String, boolean)} — reached from
     * {@code setPluginName()} whenever a boss' level-formatted name is (re)parsed — used to call
     * {@code customModel.setName(name, true)} with a hard-coded {@code true}, bypassing
     * {@code NativeDisplayPolicy} entirely. Unlike {@code setName(EliteMobProperties)} and
     * {@code setNameVisible(boolean)} (both already covered above / by {@link NativeDisplayPolicyTest}),
     * this overload never consulted {@code resolveCustomModelNametagVisible}, so the model's nametag
     * came back on every time a boss' name was reformatted regardless of
     * {@code native-display-suppression.custom-model-nametag}.
     */
    @Test
    @DisplayName("setName(String, boolean) suppresses the custom model's nametag exactly like the other overloads")
    void setNameOverloadSuppressesModelNametag() throws Exception {
        IntegrationState.set("available", true);
        IntegrationState.set("suppressCustomModelNametag", true);

        RecordingLivingEntity livingEntity = new RecordingLivingEntity();
        RecordingCustomModel model = new RecordingCustomModel();
        CustomBossEntity boss = bossWith(livingEntity.proxy(), model.proxy(), false);

        boss.setName("Some Boss", false);

        assertEquals(List.of(false), model.setNameVisibleArgs,
                "customModel.setName(name, visible) must resolve visible=false when "
                        + "native-display-suppression.custom-model-nametag is on. If this reads [true], "
                        + "the hard-coded `true` regressed (2026-08-04) and the model nametag bypasses "
                        + "suppression again.");
    }

    /** Builds a CustomBossEntity without running its heavy real constructor. */
    private static CustomBossEntity bossWith(LivingEntity livingEntity, CustomModelInterface model,
                                              boolean alwaysShowName) throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        CustomBossEntity boss = (CustomBossEntity) unsafe.allocateInstance(CustomBossEntity.class);

        CustomBossesConfigFields fields =
                (CustomBossesConfigFields) unsafe.allocateInstance(CustomBossesConfigFields.class);
        fields.setAlwaysShowName(alwaysShowName);

        setField(boss, CustomBossEntity.class, "customBossesConfigFields", fields);
        setField(boss, CustomBossEntity.class, "customModel", model);
        setField(boss, EliteEntity.class, "livingEntity", livingEntity);
        return boss;
    }

    private static void setField(Object target, Class<?> declaringClass, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    /** Records vanilla nametag writes; also answers isValid()=true so the model-tag branch runs. */
    private static final class RecordingLivingEntity implements InvocationHandler {
        LivingEntity proxy() {
            return (LivingEntity) Proxy.newProxyInstance(
                    LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "isValid":
                    return true;
                case "setCustomNameVisible":
                case "setCustomName":
                    return null;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "RecordingLivingEntity";
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    /** Records every {@code setNameVisible} and {@code setName(name, visible)} write on the custom model. */
    private static final class RecordingCustomModel implements InvocationHandler {
        private final List<Boolean> nameVisibleWrites = new ArrayList<>();
        /** The {@code visible} argument of every {@code setName(String, boolean)} call. */
        private final List<Boolean> setNameVisibleArgs = new ArrayList<>();

        CustomModelInterface proxy() {
            return (CustomModelInterface) Proxy.newProxyInstance(
                    CustomModelInterface.class.getClassLoader(), new Class<?>[]{CustomModelInterface.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setNameVisible":
                    nameVisibleWrites.add((Boolean) args[0]);
                    return null;
                case "setName":
                    setNameVisibleArgs.add((Boolean) args[1]);
                    return null;
                case "getNametagBoneLocation":
                    return (Location) null;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "RecordingCustomModel" + nameVisibleWrites;
                default:
                    return defaultValue(method.getReturnType());
            }
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
