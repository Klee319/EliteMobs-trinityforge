package com.magmaguy.elitemobs.items.potioneffects;

/**
 * 「装備由来の常時ポーション効果(continuous)を、今付いている効果へ上書きしてよいか」の判定。
 *
 * <p><b>TF fork 2026-08-18</b>: 上流の判定は残り時間を {@code existingEffect.getDuration() > 40}
 * だけで見ていた。<b>Paper では無期限効果の {@code getDuration()} は {@code -1}</b> なので、
 * 無期限効果はこの条件を満たさず「もう切れかけ」と誤判定され、
 * 1秒ごとに走る常時効果ループが<b>無期限効果を剥がして数秒の効果へ差し替えていた</b>。
 * 実害は「無期限で付けた幸運(管理コマンド / ArsPaper のスレッド由来)が数秒後に消える」
 * ── ユーザー報告「幸運のエフェクトが消える」の経路のひとつ。
 *
 * <p>Bukkit へ依存しない純関数にして、挙動をテストで固定している。
 */
public final class ContinuousPotionPolicy {

    /** 上流由来の閾値: 残りこれ以上ある効果は装備由来の短い効果で上書きしない。 */
    static final int KEEP_EXISTING_THRESHOLD_TICKS = 40;

    private ContinuousPotionPolicy() {
    }

    /**
     * 既存効果を維持する(=装備由来の効果を付けない)か。
     *
     * @param present           同じ型の効果が既に付いているか
     * @param infinite          その効果が無期限か({@code getDuration() == -1})
     * @param existingAmplifier その効果の amplifier
     * @param existingDuration  その効果の残り tick(無期限なら意味を持たない)
     * @param incomingAmplifier 装備由来の効果の amplifier
     */
    public static boolean keepsExisting(boolean present, boolean infinite, int existingAmplifier,
                                        int existingDuration, int incomingAmplifier) {
        if (!present) {
            return false;
        }
        if (existingAmplifier > incomingAmplifier) {
            return true;
        }
        if (infinite) {
            // 無期限効果は「装備由来の数秒の効果」より必ず強い(上流は -1 を切れかけと誤読していた)。
            return true;
        }
        return existingDuration > KEEP_EXISTING_THRESHOLD_TICKS;
    }
}
