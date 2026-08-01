package com.magmaguy.elitemobs.commands;

import com.magmaguy.elitemobs.api.PlayerPreTeleportEvent;
import com.magmaguy.elitemobs.config.CommandMessagesConfig;
import com.magmaguy.elitemobs.dungeons.DynamicDungeonPackage;
import com.magmaguy.elitemobs.dungeons.EMPackage;
import com.magmaguy.elitemobs.dungeons.WorldInstancedDungeonPackage;
import com.magmaguy.elitemobs.instanced.MatchInstance;
import com.magmaguy.elitemobs.menus.DynamicDungeonBrowser;
import com.magmaguy.elitemobs.menus.InstancedDungeonBrowser;
import com.magmaguy.elitemobs.playerdata.statusscreen.PlayerStatusScreenDialog;
import com.magmaguy.elitemobs.playerdata.statusscreen.TeleportsPage;
import com.magmaguy.elitemobs.trinityforge.TrinityForgeDungeonGateListener;
import org.bukkit.entity.Player;

public class DungeonCommands {
    public static void teleport(Player player, String minidungeonName) {
        teleport(player, minidungeonName, TeleportMenuSource.NONE);
    }

    public static void teleport(Player player, String minidungeonName, TeleportMenuSource teleportMenuSource) {
        EMPackage emPackage = EMPackage.getEmPackages().get(minidungeonName);
        if (emPackage == null) {
            player.sendMessage(CommandMessagesConfig.getDungeonNotValidMessage());
            return;
        } else if (!emPackage.isInstalled()) {
            player.sendMessage(CommandMessagesConfig.getDungeonNotInstalledMessage());
            return;
        }
        if (MatchInstance.getAnyPlayerInstance(player) != null) {
            player.sendMessage(CommandMessagesConfig.getAlreadyInInstanceMessage());
            return;
        }
        String contentPackage = emPackage.getContentPackagesConfigFields().getFilename();
        // TrinityForge (2026-08-01 round2): インスタンスダンジョンのブラウザだけは
        // PlayerPreTeleportEvent を通らないので、ここで入場ゲートを先読みする。
        //
        // 使うのは previewRequiredEntry 側 (previewDungeonEntryAllowed) であって
        // hasEntryGate ではない。hasEntryGate には「ゲートが1本も無いなら機能ごと無効」の
        // 逃げ道が無く、出荷時の gates.yml (gates: {}) のままだと全ダンジョンが
        // 「入場ゲートが設定されていないため入場できません」で塞がる —— TF 側が同日 113ff86 で
        // 直したばかりの封鎖を、ここで作り直すことになる。
        // preview 系はレベル/鍵を実際に評価して理由まで返し、かつ鍵を消費しない
        // (消費は参加確定時の checkDungeonEntryAllowed 側の仕事)。
        if ((emPackage instanceof DynamicDungeonPackage || emPackage instanceof WorldInstancedDungeonPackage)
                && !TrinityForgeDungeonGateListener.previewDungeonEntryAllowed(player, contentPackage)) {
            return;
        }
        if (emPackage instanceof DynamicDungeonPackage)
            new DynamicDungeonBrowser(player, contentPackage, teleportMenuSource);
        else if (emPackage instanceof WorldInstancedDungeonPackage)
            new InstancedDungeonBrowser(player, contentPackage, teleportMenuSource);
        else {
            if (emPackage.getContentPackagesConfigFields().getTeleportLocation() != null) {
                PlayerPreTeleportEvent.teleportPlayer(player, emPackage.getContentPackagesConfigFields().getTeleportLocation());
            }
            else
                player.sendMessage(CommandMessagesConfig.getDungeonTeleportNotSetMessage());
        }
    }

    public static void reopenTeleportBrowser(Player player, TeleportMenuSource teleportMenuSource) {
        switch (teleportMenuSource) {
            case INVENTORY -> TeleportsPage.showTeleportInventory(player);
            case DIALOGUE -> PlayerStatusScreenDialog.showTeleportsDialog(player);
            case NONE -> {
            }
        }
    }

    public enum TeleportMenuSource {
        NONE,
        INVENTORY,
        DIALOGUE
    }
}
