package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * per-CALL-SITE coverage for the 2026-08-04 nametag fix that
 * {@link TrinityForgeGateWiringTest} structurally cannot see.
 *
 * <h2>Why the per-class check is blind here</h2>
 * {@code TrinityForgeGateWiringTest#displaySuppressionIsWired} scans {@code EliteEntity}'s whole
 * constant pool for {@code resolveNametagVisible}. {@code EliteEntity#setNameVisible} calls that
 * policy method itself, so the reference is in the pool no matter what — deleting the
 * {@code setNameVisible(...)} call from {@code setLivingEntity} leaves every existing test green.
 * That was verified by mutation on 2026-08-05: removing the line broke nothing.
 *
 * <h2>What the missing call site actually costs</h2>
 * {@code setLivingEntity} only runs {@code setName(...)} when {@code getName() == null}, and
 * {@code setName} is the sole other path that resolves visibility. So for any elite that already
 * carries a name — <b>persistent bosses, entities re-tracked on chunk load, and anything that was in
 * the world before suppression was switched on</b> — visibility was never re-resolved. Nametag
 * visibility lives in the entity's NBT ({@code CustomNameVisible}), so those mobs kept their nametags
 * forever (the live-server report "the text above EliteMobs mobs still is not gone").
 *
 * <p>Re-resolving unconditionally is safe because suppression can only ever HIDE, never force-show
 * ({@link NativeDisplayPolicy}) — with suppression off the value passes through unchanged.
 *
 * <p>Asserted by disassembling with the JDK's own {@code javap} and slicing out just that one
 * method's block ({@link Javap}), the same technique as {@link CurrencyShowerCallSiteTest}: driving
 * {@code setLivingEntity} behaviourally would need a live Bukkit server with a spawned entity, which
 * this fork's tests deliberately do not fake.
 */
class NametagReresolveCallSiteTest {

    @Test
    @DisplayName("EliteEntity#setLivingEntity re-resolves nametag visibility for already-named elites")
    void setLivingEntityReresolvesNametagVisibility() throws Exception {
        String disassembly = Javap.disassemble(EliteEntity.class);
        String method = Javap.sliceMethod(disassembly,
                "void setLivingEntity(org.bukkit.entity.LivingEntity,");

        assertTrue(method.contains("setNameVisible"),
                "setLivingEntity no longer calls setNameVisible(...), so an elite that already has a "
                        + "name never re-resolves its nametag visibility: setName(...) — the only other "
                        + "resolving path — runs solely when getName() == null. Persistent bosses, "
                        + "chunk-reload re-tracked entities and pre-suppression mobs keep their "
                        + "nametags forever because CustomNameVisible is persisted in NBT.\n"
                        + "TrinityForgeGateWiringTest CANNOT catch this: setNameVisible itself "
                        + "references resolveNametagVisible, so the class-level constant-pool check "
                        + "stays green (verified by mutation on 2026-08-05).\n" + method);
        assertTrue(method.contains("isCustomNameVisible"),
                "setLivingEntity no longer reads the entity's CURRENT visibility as the input to "
                        + "setNameVisible. Passing a constant instead would either force-show nametags "
                        + "(suppression off) or silently diverge from the entity's own NBT state.\n"
                        + method);
    }
}
