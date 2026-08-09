package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.items.customloottable.SharedLootTable;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrinityForge の追加ドロップを共有戦利品テーブル(emloot)へ流す経路の固定テスト (2026-08-09)。
 * <p>
 * 分配そのもの ({@code SharedLootTable#distribute}) は live な Bukkit サーバ (実プレイヤー・
 * インベントリ・スケジューラ) が要るのでユニットテストから駆動できない。そこで
 * {@link CurrencyShowerCallSiteTest} と同じく {@link Javap} で <b>その1メソッドのバイトコードだけ</b>
 * を切り出し、「TrinityForge のアイテムには EliteMobs の後処理を掛けない」というガードが
 * 実在することを固定する。ガードを消す / 順序を入れ替えるとここが落ちる。
 */
class SharedLootTableTrinityForgeTest {

    @Test
    @DisplayName("rollLoot は EliteMobs の後処理3つを trinityForgeLoot の判定より後ろでしか呼ばない")
    void trinityForgeLootSkipsEliteMobsPostProcessing() throws IOException, InterruptedException {
        String rollLoot = Javap.sliceMethod(Javap.disassemble(SharedLootTable.class), "rollLoot(");

        int guard = rollLoot.indexOf("trinityForgeLoot");
        assertTrue(guard >= 0, "rollLoot が trinityForgeLoot を読まなくなっている — TrinityForge の "
                + "アイテムにも EliteMobs のソウルバインド/ロア/エリートレベルが掛かる状態に戻っている:\n"
                + rollLoot);
        assertTrue(rollLoot.indexOf("Set.contains", guard) >= 0 || rollLoot.indexOf("contains", guard) >= 0,
                "trinityForgeLoot は読んでいるが contains していない — 判定が骨抜きになっている:\n" + rollLoot);

        // 3つの後処理はどれも判定より後ろにあること。前に出ていたら判定を通さず掛かってしまう。
        for (String postProcessing : new String[]{"addEnchantment", "EliteItemLore", "setEliteLevel"}) {
            int at = rollLoot.indexOf(postProcessing);
            assertTrue(at >= 0, "rollLoot が " + postProcessing + " を呼ばなくなっている — EliteMobs 由来の "
                    + "戦利品に対する上流の挙動まで消えている:\n" + rollLoot);
            assertTrue(at > guard, postProcessing + " が trinityForgeLoot の判定より前にある — "
                    + "TrinityForge のアイテムにも掛かってしまう:\n" + rollLoot);
        }

        // 判定と後処理の間に条件分岐が要る。分岐が無ければ「読んではいるが何も分けていない」。
        String betweenGuardAndPostProcessing = rollLoot.substring(guard, rollLoot.indexOf("addEnchantment"));
        assertTrue(betweenGuardAndPostProcessing.contains("ifeq") || betweenGuardAndPostProcessing.contains("ifne"),
                "判定結果で分岐していない — contains の戻り値が捨てられている:\n" + rollLoot);
    }

    @Test
    @DisplayName("addTrinityForgeLoot は addLoot と別経路で残っている")
    void addTrinityForgeLootExists() throws NoSuchMethodException {
        Method method = SharedLootTable.class.getMethod("addTrinityForgeLoot", ItemStack.class);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("offerDungeonLoot の署名は TrinityForge 側のリフレクションと一致している")
    void offerDungeonLootSignatureMatchesTheTrinityForgeBridge() throws NoSuchMethodException {
        // TrinityForge の com.trinityforge.mobs.EliteMobsSharedLootBridge は
        // Class.forName("com.magmaguy.elitemobs.trinityforge.TrinityForgeSharedLoot") と
        // getMethod("offerDungeonLoot", Entity.class, ItemStack.class) でここを呼ぶ。
        // 名前・引数・戻り値のどれを変えても向こう側はコンパイルが通ったまま無言で
        // fail-soft (地面へ落とす) に戻るため、ここで署名を固定する。
        Method method = TrinityForgeSharedLoot.class.getMethod("offerDungeonLoot", Entity.class, ItemStack.class);
        assertTrue(Modifier.isPublic(method.getModifiers()), "TrinityForge から呼ぶので public 必須");
        assertTrue(Modifier.isStatic(method.getModifiers()), "リフレクション呼び出しは static 前提");
        assertEquals(boolean.class, method.getReturnType(), "「引き取ったか」を boolean で返す契約");
        assertEquals("com.magmaguy.elitemobs.trinityforge.TrinityForgeSharedLoot",
                TrinityForgeSharedLoot.class.getName(), "完全修飾名も TrinityForge 側の定数と一致させること");
    }

    @Test
    @DisplayName("TrinityForgeSharedLoot は共有テーブル生成とダンジョン判定を両方通す")
    void sharedLootEntryPointIsWired() throws IOException {
        String bytecode = constantPoolOf("com/magmaguy/elitemobs/trinityforge/TrinityForgeSharedLoot");
        for (String reference : new String[]{
                "SharedLootTable", "getSharedLootTables", "addTrinityForgeLoot",
                "DungeonInstance", "getDamagers"}) {
            assertTrue(bytecode.contains(reference),
                    "TrinityForgeSharedLoot が " + reference + " を参照しなくなっている — "
                            + "引き取り条件か共有テーブルへの投入が消えている。");
        }
    }

    /** {@code TrinityForgeGateWiringTest} と同じ、生バイト列を ISO-8859-1 で読む定数プール検査。 */
    private static String constantPoolOf(String internalName) throws IOException {
        try (InputStream in = SharedLootTableTrinityForgeTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, "compiled class not found on the test classpath: " + internalName);
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
