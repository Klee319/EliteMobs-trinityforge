package com.magmaguy.elitemobs.commands;

import com.magmaguy.elitemobs.config.CommandMessagesConfig;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.arguments.IntegerCommandArgument;
import com.magmaguy.magmacore.command.arguments.PlayerCommandArgument;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class SkillSetAllCommand extends AdvancedCommand {
    public SkillSetAllCommand() {
        super(List.of("skill"));
        addLiteral("setAll");
        addArgument("player", new PlayerCommandArgument());
        addArgument("level", new IntegerCommandArgument("<level>"));
        setUsage("/em skill setAll <player> <level>");
        setPermission("elitemobs.skill.admin");
        setDescription("Sets all of a player's skills to the specified level.");
    }

    @Override
    public void execute(CommandData commandData) {
        String playerName = commandData.getStringArgument("player");
        int level = commandData.getIntegerArgument("level");

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            Logger.sendMessage(commandData.getCommandSender(), CommandMessagesConfig.getSkillPlayerNotFoundMessage().replace("$player", playerName));
            return;
        }

        if (level < 1) {
            Logger.sendMessage(commandData.getCommandSender(), CommandMessagesConfig.getSkillLevelMinMessage());
            return;
        }

        // 武器スキルはTrinityForgeへ一本化済み。
        // PlayerData.setSkillXPは常にno-opのため、ここで実行すると
        // 実際には何も変更されないのに成功メッセージだけ表示される「偽の成功」になる。
        // それを避けるため、明示的な無効化メッセージを返して処理を打ち切る。
        Logger.sendMessage(commandData.getCommandSender(),
                "武器スキルはTrinityForgeへ一本化されているため、このEliteMobsコマンドは無効です。");
    }
}
