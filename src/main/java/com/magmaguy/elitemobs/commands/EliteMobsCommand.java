package com.magmaguy.elitemobs.commands;

import com.magmaguy.elitemobs.config.DefaultConfig;
import com.magmaguy.elitemobs.playerdata.statusscreen.PlayerStatusScreen;
import com.magmaguy.elitemobs.trinityforge.TrinityForgeIntegration;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.SenderType;
import com.magmaguy.magmacore.util.Logger;

import java.util.ArrayList;

public class EliteMobsCommand extends AdvancedCommand {
    public EliteMobsCommand() {
        super(new ArrayList<>());
        setDescription("The main command for EliteMobs, opens the main menu.");
        setUsage("/em");
        setPermission("elitemobs.command");
        setSenderType(SenderType.PLAYER);
    }

    @Override
    public void execute(CommandData commandData) {
        // TrinityForge owns player progression / status — do not open EM's status menu.
        if (TrinityForgeIntegration.isAvailable()) {
            Logger.sendMessage(commandData.getCommandSender(),
                    "&cEliteMobs player menu is disabled. Use &e/skills &cfor progression.");
            return;
        }
        if (DefaultConfig.isEmLeadsToStatusMenu())
            new PlayerStatusScreen(commandData.getPlayerSender());
    }
}
