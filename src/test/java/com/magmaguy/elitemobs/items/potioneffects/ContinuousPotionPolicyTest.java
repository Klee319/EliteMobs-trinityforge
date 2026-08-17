package com.magmaguy.elitemobs.items.potioneffects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TF fork 2026-08-18: 装備由来の常時ポーション効果が<b>無期限効果を上書きしない</b>ことを固定する。
 *
 * <p>直したバグ: 上流の判定は残り時間を {@code getDuration() > 40} だけで見ていた。
 * Paper では無期限効果の {@code getDuration()} は {@code -1} なので、この条件を満たさず
 * 「切れかけ」と誤判定され、1秒ごとに走る常時効果ループが無期限効果を剥がして
 * 数秒の効果へ差し替えていた(無期限で付けた幸運が数秒で消える)。
 */
class ContinuousPotionPolicyTest {

    private static final int INFINITE_DURATION = -1;

    @Test
    @DisplayName("無期限効果は装備由来の常時効果で上書きしない(getDuration()==-1 を切れかけと誤読しない)")
    void infiniteEffectIsNeverOverwritten() {
        assertTrue(ContinuousPotionPolicy.keepsExisting(true, true, 0, INFINITE_DURATION, 0),
                "無期限効果(残り-1)を『切れかけ』と誤判定して上書きしている"
                        + "(無期限の幸運が数秒で消える不具合の真因)");
        assertTrue(ContinuousPotionPolicy.keepsExisting(true, true, 0, INFINITE_DURATION, 3),
                "装備側の amplifier が高くても、無期限効果を短い効果へ差し替えてはいけない");
    }

    @Test
    @DisplayName("残り時間が閾値より長い有限効果も維持する(上流の規約をそのまま保つ)")
    void longerFiniteEffectIsKept() {
        assertTrue(ContinuousPotionPolicy.keepsExisting(true, false, 0, 200, 0));
        assertFalse(ContinuousPotionPolicy.keepsExisting(true, false, 0, 20, 0),
                "残り1秒の効果は装備由来の効果で付け直してよい(付け直しが止まると常時効果が切れる)");
    }

    @Test
    @DisplayName("既存効果のほうが強ければ格下げしない")
    void strongerExistingEffectIsKept() {
        assertTrue(ContinuousPotionPolicy.keepsExisting(true, false, 2, 20, 0));
        assertFalse(ContinuousPotionPolicy.keepsExisting(true, false, 0, 20, 2),
                "装備側のほうが強いのに付与を諦めている");
    }

    @Test
    @DisplayName("そもそも付いていなければ付与する")
    void absentEffectIsApplied() {
        assertFalse(ContinuousPotionPolicy.keepsExisting(false, false, Integer.MIN_VALUE, 0, 0));
    }
}
