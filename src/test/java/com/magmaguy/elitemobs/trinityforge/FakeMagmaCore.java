package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.magmacore.MagmaCore;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Installs a minimal {@link MagmaCore} singleton so production code that logs through MagmaCore's
 * {@code Logger} ({@code Logger.warn}/{@code info} both call
 * {@code MagmaCore.getInstance().getRequestingPlugin().getName()} — confirmed by decompiling
 * {@code MagmaCore-2.2.0-SNAPSHOT.jar}'s {@code Logger.class}) can be exercised from a plain unit test.
 * <p>
 * {@code MagmaCore}'s real constructor has heavy side effects that need a fully running server (menu
 * handlers, biome-compatibility mappings, Nightbreak account init, its own {@code Logger.info} call) and
 * is private besides. Rather than reproduce all of that, this bypasses the constructor entirely via
 * {@link Unsafe#allocateInstance} — the same "raw object, no constructor run" trick (de)serialization
 * frameworks use — and pokes only what {@code Logger.warn}'s call chain actually reads:
 * {@code MagmaCore.instance} (static) -&gt; {@code requestingPlugin} (field) -&gt; {@code getName()}
 * (spigot-api's {@code PluginBase.getName()} is {@code final} and delegates to)
 * {@code JavaPlugin.getDescription()} (also {@code final}, reads the private {@code description} field)
 * -&gt; {@code PluginDescriptionFile.getName()}. Every other {@code JavaPlugin}/{@code PluginBase} field
 * is left {@code null}/default, which is safe because nothing else in the call chain under test ever
 * touches them.
 */
final class FakeMagmaCore {

    private static boolean installed = false;

    private FakeMagmaCore() {
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            Unsafe unsafe = unsafe();
            JavaPlugin plugin = (JavaPlugin) unsafe.allocateInstance(TestJavaPlugin.class);
            setDescription(plugin, new PluginDescriptionFile("FakeMagmaCorePlugin", "1.0", "test.Main"));

            MagmaCore core = (MagmaCore) unsafe.allocateInstance(MagmaCore.class);
            Field requestingPlugin = MagmaCore.class.getDeclaredField("requestingPlugin");
            requestingPlugin.setAccessible(true);
            requestingPlugin.set(core, plugin);

            Field instance = MagmaCore.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, core);

            installed = true;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not install FakeMagmaCore — MagmaCore's or JavaPlugin's field "
                    + "layout changed; update this test helper, not the tests that depend on it: " + e, e);
        }
    }

    private static void setDescription(JavaPlugin plugin, PluginDescriptionFile description)
            throws ReflectiveOperationException {
        Field descriptionField = JavaPlugin.class.getDeclaredField("description");
        descriptionField.setAccessible(true);
        descriptionField.set(plugin, description);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    /** Never actually constructed (see class javadoc) — only reflectively poked fields are read. */
    static final class TestJavaPlugin extends JavaPlugin {
    }
}
