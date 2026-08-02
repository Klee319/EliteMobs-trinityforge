package com.magmaguy.elitemobs.utils;

import com.magmaguy.elitemobs.MetadataHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.List;

public class SimpleScoreboard {

    /**
     * クエスト追跡用のサイドバーを表示するたびに {@code getNewScoreboard()} で真っ白な
     * {@link Scoreboard} を割り当てている(このクラスとクエスト側の3箇所)。サイドバーはプレイヤーごとに
     * 内容が異なる(1スコアボードに1つしか持てない)ため main スコアボードの使い回しは採れないが、
     * これによりプレイヤーが見ている(=クライアントへ配信される)スコアボードが main から外れるため、
     * main 側に登録された他プラグインのチーム(prefix/suffix によるネームタグ拡張、
     * TrinityForge の称号表示など)がそのプレイヤーの視界からだけ消える副作用がある
     * (TrinityForge バグ報告 2026-08 再発分の原因のひとつ)。しかも {@code stop()} 後も
     * main へは戻らないため、一度クエストを追跡すると再ログインするまで直らない。
     *
     * <p>ここで新規スコアボードを作るたびに main のチーム定義(entries/prefix/suffix/色/オプション)を
     * 複製することで、EM のサイドバーと他プラグインのチーム表示を両立させる。{@link #lazyScoreboard}
     * はクエスト進捗が更新されるたびに毎回呼ばれるため、main 側のチームが後から変わっても
     * (称号の付け替え等)次の進捗更新で自然に追随する。
     */
    public static void copyMainTeamsInto(Scoreboard target) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard main = manager.getMainScoreboard();
        if (main == target) return;
        for (Team source : main.getTeams()) {
            if (target.getTeam(source.getName()) != null) continue;
            Team copy = target.registerNewTeam(source.getName());
            try {
                // このフォークは spigot-api でコンパイルしているので Adventure 版の
                // prefix(Component)/suffix(Component)/hasColor() は存在しない。レガシーの
                // String API を使う(実行時は Paper なので §x のグラデーションも往復できる)。
                copy.setPrefix(source.getPrefix());
                copy.setSuffix(source.getSuffix());
                copy.setColor(source.getColor());
                copy.setAllowFriendlyFire(source.allowFriendlyFire());
                copy.setCanSeeFriendlyInvisibles(source.canSeeFriendlyInvisibles());
                copy.setNameTagVisibility(source.getNameTagVisibility());
                for (String entry : source.getEntries()) {
                    copy.addEntry(entry);
                }
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // 複製元/複製先どちらかが直後に unregister された等の競合。次回呼び出しで再同期される。
            }
        }
    }

    public static Scoreboard lazyScoreboard(Player player, String displayName, List<String> scoreboardContents) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        copyMainTeamsInto(scoreboard);
        int lineCount = Math.min(scoreboardContents.size(), 15);

        Objective objective = scoreboard.registerNewObjective("test", Criteria.DUMMY, displayName);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < lineCount; i++) {
            String scoreString = scoreboardContents.get(i);
            if (scoreString.length() > 40) scoreString = scoreString.substring(0, 39);
            Score score = objective.getScore(scoreString);
            score.setScore(i);
        }

        player.setScoreboard(scoreboard);

        return scoreboard;
    }

    public static Scoreboard temporaryScoreboard(Player player, String displayName, List<String> scoreboardContents, int ticksTimeout) {
        Scoreboard scoreboard = lazyScoreboard(player, displayName, scoreboardContents);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getScoreboard().equals(scoreboard)) {
                    Scoreboard blank = Bukkit.getScoreboardManager().getNewScoreboard();
                    copyMainTeamsInto(blank);
                    player.setScoreboard(blank);
                }
            }
        }.runTaskLater(MetadataHandler.PLUGIN, ticksTimeout);

        return scoreboard;
    }
}
