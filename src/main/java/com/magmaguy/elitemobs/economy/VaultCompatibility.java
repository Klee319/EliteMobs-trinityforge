package com.magmaguy.elitemobs.economy;

import com.magmaguy.elitemobs.config.EconomySettingsConfig;
import com.magmaguy.magmacore.util.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class VaultCompatibility {

    public static boolean VAULT_ENABLED = false;
    private static Economy econ = null;

    public static void vaultSetup() {
        if (Bukkit.getServer().getPluginManager().isPluginEnabled("Vault")) {
            Logger.info("[EliteMobs] Vault detected.");
            if (EconomySettingsConfig.isUseVault()) {
                Logger.info("[TrinityForge] TrinityForge構成では意図的にVault経済と連携します" +
                        "(useVault: true)。Vaultの経済プロバイダ(例: EssentialsX等)が未導入の場合は" +
                        "自動的にEliteMobs内蔵通貨システムへフォールバックします。");
                VaultCompatibility.setupEconomy();
                if (econ != null) {
                    VAULT_ENABLED = true;
                } else {
                    Logger.warn("[EliteMobs] Vaultは検出されましたが、経済プロバイダ(Economy)が" +
                            "登録されていません。EssentialsX等の経済プラグインを導入するか、" +
                            "useVault を false にしてください。今回はEliteMobs内蔵通貨システムに" +
                            "フォールバックします。");
                    VAULT_ENABLED = false;
                }
            }

        }
    }

    public static void setupEconomy() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        econ = rsp.getProvider();
    }

    public static void addVaultCurrency(UUID user, double amount) {
        if (econ == null) {
            Logger.warn("[EliteMobs] Vault currency was requested but no economy provider is available." +
                    " This amount was not credited: " + amount);
            return;
        }
        econ.depositPlayer(Bukkit.getOfflinePlayer(user), amount);
    }

    public static void subtractCurrency(UUID user, double amount) {
        if (econ == null) {
            Logger.warn("[EliteMobs] Vault currency was requested but no economy provider is available." +
                    " This amount was not debited: " + amount);
            return;
        }
        econ.withdrawPlayer(Bukkit.getOfflinePlayer(user), amount);
    }

    public static void setCurrency(UUID user, double amount) {
        Logger.warn("Someone just attempted to set the Vault currency through EliteMobs." +
                " For safety reasons, this command is off. Please use your dedicated economy plugin for this.");
    }

    public static double checkCurrency(UUID user) {
        if (econ == null) return 0;
        double currency = 0;
        try {
            currency = econ.getBalance(Bukkit.getOfflinePlayer(user));
        } catch (NullPointerException e) {
            Logger.warn("Player tried to check currency when they had no economy entry" +
                    "associated to them. This is an issue with your Vault/economy implementation, not EliteMobs.");
        }
        return currency;

    }

    public static Economy getEconomy() {
        return econ;
    }

}
