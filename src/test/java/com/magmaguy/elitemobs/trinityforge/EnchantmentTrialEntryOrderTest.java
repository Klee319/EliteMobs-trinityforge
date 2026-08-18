package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.instanced.dungeons.DungeonInstance;
import com.magmaguy.elitemobs.instanced.dungeons.EnchantmentDungeonInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-08-18 実サーバ報告の2件を固定する。
 * <ul>
 *   <li>「4人ぶんのエンチャント試練1の鍵を作ったのに1人しか入れず、残り3人は鍵だけ消えた」<br>
 *       {@code enchantment_challenge_*_sanctum} は {@code maxPlayerCount: 1}。ブラウザから既存インスタンスへ
 *       参加した2人目以降は {@code MatchInstance#addNewPlayer} が「満員」で false を返すが、TrinityForge の
 *       鍵消費はその <b>手前</b> で走っていたため鍵だけ取られた。</li>
 *   <li>「入ったあとスタートすると敵が一瞬で消え、{@code /em quit} でも帰れなくなった」<br>
 *       鍵ゲート経由で入場した {@link EnchantmentDungeonInstance} は賭けアイテム(currentItem/upgradedItem)を
 *       持たないので {@code defeat()} が NPE を投げ、例外が呼び出し元へ抜けて脱出処理を丸ごと飛ばしていた。</li>
 * </ul>
 *
 * <p><b>なぜ逆アセンブルで見るのか。</b> どちらも実行には生きた Bukkit サーバ(ワールドのクローン・参加者・
 * PlayerData)が要り、ユニットテストからは駆動できない。まさにそれが「本番でだけ静かに壊れていた」理由なので、
 * {@link Javap} を使って <b>その1メソッドの中での呼び出し順</b> を固定する({@code CurrencyShowerCallSiteTest} /
 * {@link NametagReresolveCallSiteTest} と同じ手法・同じ理由)。名前の出現有無ではなく順序を見るので、
 * 「消費を super の前へ戻す」「null ガードを消す」のどちらの退行でも落ちる。
 */
class EnchantmentTrialEntryOrderTest {

    private static final String PREVIEW = "previewDungeonEntryAllowed";
    private static final String CONSUME = "checkDungeonEntryAllowed";
    private static final String SUPER_JOIN = "MatchInstance.addNewPlayer";

    private static String slice(Class<?> clazz, String methodSignaturePart) throws Exception {
        return Javap.sliceMethod(Javap.disassemble(clazz), methodSignaturePart);
    }

    @Test
    @DisplayName("鍵の消費は満員/開催中で弾かれる可能性のある参加処理の後に行う")
    void keyIsConsumedOnlyAfterTheJoinActuallySucceeded() throws Exception {
        String body = slice(DungeonInstance.class, "boolean addNewPlayer(");

        int preview = body.indexOf(PREVIEW);
        int superJoin = body.indexOf(SUPER_JOIN);
        int consume = body.indexOf(CONSUME);

        assertTrue(preview >= 0, "非消費の事前判定(previewDungeonEntryAllowed)が addNewPlayer から消えている:\n" + body);
        assertTrue(superJoin >= 0, "super.addNewPlayer の呼び出しが見つからない:\n" + body);
        assertTrue(consume >= 0, "鍵消費(checkDungeonEntryAllowed)が addNewPlayer から消えている:\n" + body);

        assertTrue(preview < superJoin,
                "入場可否の判定は参加処理より前に行うこと(高コストな参加処理に入る前に弾く):\n" + body);
        assertTrue(consume > superJoin,
                "鍵の消費は super.addNewPlayer が成功した後に行うこと。"
                        + "手前で消費すると maxPlayerCount: 1 のエンチャント試練で"
                        + "「満員で入れないのに鍵だけ消える」が再発する:\n" + body);
    }

    @Test
    @DisplayName("賭けアイテムを持たない個体では攻略失敗処理が賭けアイテムに触れない")
    void defeatChecksTheEnchantmentStakeBeforeTouchingTheStakedItem() throws Exception {
        assertStakeCheckedFirst(slice(EnchantmentDungeonInstance.class, "void defeat("), "defeat");
    }

    @Test
    @DisplayName("賭けアイテムを持たない個体では攻略成功処理が賭けアイテムに触れない")
    void victoryChecksTheEnchantmentStakeBeforeTouchingTheStakedItem() throws Exception {
        assertStakeCheckedFirst(slice(EnchantmentDungeonInstance.class, "void victory("), "victory");
    }

    private static void assertStakeCheckedFirst(String body, String methodName) {
        int stakeCheck = body.indexOf("hasEnchantmentStake");
        assertTrue(stakeCheck >= 0,
                methodName + "() から賭けアイテムの有無判定(hasEnchantmentStake)が消えている。"
                        + "鍵ゲート経由で入場した個体は currentItem/upgradedItem が null なので"
                        + "NPE で脱出処理ごと飛ぶ:\n" + body);

        // 賭けアイテムを読む命令は必ずガードより後にあること。javap では getfield / invokevirtual として
        // 出るので、フィールド名で位置を取る(getItemMeta だけを見ると addItem 側の退行を見逃す)。
        for (String stakeAccess : new String[]{"currentItem", "upgradedItem"}) {
            int firstAccess = body.indexOf(stakeAccess);
            if (firstAccess < 0) continue;
            assertTrue(firstAccess > stakeCheck,
                    methodName + "() が hasEnchantmentStake より前に " + stakeAccess + " へ触っている:\n" + body);
        }
    }
}
