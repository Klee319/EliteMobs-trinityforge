package com.magmaguy.elitemobs.commands;

import com.magmaguy.elitemobs.config.CommandMessagesConfig;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.arguments.PlayerCommandArgument;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class SkillCheckCommand extends AdvancedCommand {
    public SkillCheckCommand() {
        super(List.of("skill"));
        addLiteral("check");
        addArgument("player", new PlayerCommandArgument());
        setUsage("/em skill check <player>");
        setPermission("elitemobs.skill.check");
        setDescription("Displays a player's skill levels.");
    }

    @Override
    public void execute(CommandData commandData) {
        String playerName = commandData.getStringArgument("player");

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            Logger.sendMessage(commandData.getCommandSender(), CommandMessagesConfig.getSkillPlayerNotFoundMessage().replace("$player", playerName));
            return;
        }

        // 武器スキルはTrinityForgeへ一本化済み。
        // PlayerData.getSkillXPは常に0を返すため、ここで表示するとレベル0が
        // あたかも意味のあるデータであるかのように誤解を招く「偽の成功」になる。
        // それを避けるため、EliteMobs側のスキルデータは使用されていない旨を通知する。
        Logger.sendSimpleMessage(commandData.getCommandSender(),
                "武器スキルはTrinityForgeへ一本化されているため、EliteMobsのスキルデータは使用されていません。");
    }
}
