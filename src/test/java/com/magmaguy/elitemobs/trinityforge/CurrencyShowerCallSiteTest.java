package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.items.LootTables;
import com.magmaguy.elitemobs.items.customloottable.CurrencyCustomLootEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HIGH-1 (2026-08-01): per-CALL-SITE coverage for the two currency-shower mutations that
 * {@link TrinityForgeGateWiringTest} (a per-CLASS constant-pool check) cannot see, because in both
 * cases the class as a whole still references {@code EliteDropPolicy.shouldRunCurrencyShower} — only
 * one specific method's own bytecode lost its gate.
 * <ul>
 *   <li>{@code CurrencyCustomLootEntry#directDrop}: the gate was deleted while
 *   {@code #locationDrop} kept it, so the class-level check still found
 *   {@code "shouldRunCurrencyShower"} somewhere and passed.</li>
 *   <li>{@code LootTables#generatePlayerLoot}: the {@code eliteEntity.getPower("bonus_coins.yml") !=
 *   null} argument was replaced with a literal {@code false} — the CALL to
 *   {@code shouldRunCurrencyShower} was untouched, so the class-level check (which only looks for the
 *   method NAME, not its arguments) could never have caught this regardless of call-site precision.
 *   The only reliable signal left is that the {@code "bonus_coins.yml"} string constant — which javac
 *   only emits for the {@code getPower("bonus_coins.yml")} call — disappears from the method's own
 *   bytecode along with it.</li>
 * </ul>
 * Both are asserted by disassembling the compiled class with the JDK's own {@code javap} and slicing
 * out just the one method's block (see {@link Javap}), rather than driving the methods behaviourally:
 * {@code directDrop} needs a working economy/database backend and {@code generatePlayerLoot} needs a
 * live spawned elite with damagers, neither of which this fork's test doubles provide (and per this
 * task's constraints, must not be faked with MockBukkit).
 */
class CurrencyShowerCallSiteTest {

    @Test
    @DisplayName("CurrencyCustomLootEntry#locationDrop calls shouldRunCurrencyShower")
    void locationDropCallsTheGate() throws Exception {
        String disassembly = Javap.disassemble(CurrencyCustomLootEntry.class);
        String method = Javap.sliceMethod(disassembly, "void locationDrop(");
        assertTrue(method.contains("shouldRunCurrencyShower"),
                "locationDrop no longer calls EliteDropPolicy.shouldRunCurrencyShower — the "
                        + "elite-drop-sources.currency-shower switch is now dead for this path.\n" + method);
    }

    @Test
    @DisplayName("CurrencyCustomLootEntry#directDrop calls shouldRunCurrencyShower (its own gate, not locationDrop's)")
    void directDropCallsTheGate() throws Exception {
        String disassembly = Javap.disassemble(CurrencyCustomLootEntry.class);
        String method = Javap.sliceMethod(disassembly, "void directDrop(");
        assertTrue(method.contains("shouldRunCurrencyShower"),
                "directDrop no longer calls EliteDropPolicy.shouldRunCurrencyShower. This exact "
                        + "regression happened once (HIGH-1, 2026-08-01): the gate was deleted from "
                        + "directDrop only, locationDrop kept it, and TrinityForgeGateWiringTest's "
                        + "per-CLASS check stayed green because the class still referenced the method "
                        + "name via locationDrop.\n" + method);
    }

    @Test
    @DisplayName("LootTables#generatePlayerLoot passes the real bonus_coins.yml check, not a hardcoded literal")
    void generatePlayerLootPassesTheRealArgument() throws Exception {
        String disassembly = Javap.disassemble(LootTables.class);
        String method = Javap.sliceMethod(disassembly, "void generatePlayerLoot(");
        assertTrue(method.contains("shouldRunCurrencyShower"),
                "generatePlayerLoot no longer calls EliteDropPolicy.shouldRunCurrencyShower at all.\n"
                        + method);
        assertTrue(method.contains("bonus_coins.yml"),
                "generatePlayerLoot's call to shouldRunCurrencyShower no longer reads the "
                        + "bonus_coins.yml power (javac would only emit this string constant here for "
                        + "eliteEntity.getPower(\"bonus_coins.yml\")). This exact regression happened "
                        + "once (HIGH-1, 2026-08-01): the argument was hardcoded to a literal false, "
                        + "the shouldRunCurrencyShower call site itself was untouched, and "
                        + "TrinityForgeGateWiringTest's name-only check could never have caught it.\n"
                        + method);
    }
}
