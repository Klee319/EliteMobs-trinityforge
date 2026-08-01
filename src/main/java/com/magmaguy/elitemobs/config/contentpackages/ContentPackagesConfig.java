package com.magmaguy.elitemobs.config.contentpackages;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.dungeons.EMPackage;
import com.magmaguy.magmacore.config.CustomConfig;
import lombok.Getter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ContentPackagesConfig extends CustomConfig {

    @Getter
    private static Map<String, ContentPackagesConfigFields> dungeonPackages = new HashMap<>();
    @Getter
    private static Map<String, ContentPackagesConfigFields> enchantedChallengeDungeonPackages = new HashMap<>();


    public ContentPackagesConfig() {
        super("content_packages", "com.magmaguy.elitemobs.config.contentpackages.premade", ContentPackagesConfigFields.class);
        dungeonPackages = new HashMap<>();
        enchantedChallengeDungeonPackages = new HashMap<>();
        for (String key : super.getCustomConfigFieldsHashMap().keySet()) {
            if (!((ContentPackagesConfigFields) super.getCustomConfigFieldsHashMap().get(key)).isEnchantmentChallenge())
                dungeonPackages.put(key, (ContentPackagesConfigFields) super.getCustomConfigFieldsHashMap().get(key));
            else
                enchantedChallengeDungeonPackages.put(key, (ContentPackagesConfigFields) super.getCustomConfigFieldsHashMap().get(key));
        }

        //Initialize blueprints folder
        File worldsBluePrint = new File(MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath() + File.separatorChar + "world_blueprints");
        if (!worldsBluePrint.exists()) worldsBluePrint.mkdir();
    }

    /**
     * Looks a content package up by its config filename across BOTH indexes.
     * <p>
     * The constructor deliberately splits {@code isEnchantmentChallenge()} packages out of
     * {@link #getDungeonPackages()} (they must not appear in the normal dungeon teleport lists), which
     * means {@code getDungeonPackages().get(filename)} returns {@code null} for every
     * {@code enchantment_challenge_*_sanctum.yml}. Any code that resolves a package the player is
     * actually trying to ENTER must use this method instead: an enchantment-challenge sanctum is a
     * perfectly ordinary instanced dungeon once it is being entered, and
     * {@code DungeonInstance#initializeInstancedWorld} already branches on
     * {@code isEnchantmentChallenge()} to build the right instance type.
     *
     * @param filename the content-package config filename, always with the {@code .yml} suffix
     * @return the fields, or {@code null} when no package with that filename exists
     */
    public static ContentPackagesConfigFields getAnyPackage(String filename) {
        if (filename == null) return null;
        ContentPackagesConfigFields fields = dungeonPackages.get(filename);
        return fields != null ? fields : enchantedChallengeDungeonPackages.get(filename);
    }

    /**
     * Re-reads dungeonVersion from disk for all content packages.
     * Must be called after the importer extracts new content (which overwrites YAML files)
     * but before initializePackages() and VersionChecker.check().
     */
    public static void refreshDungeonVersions() {
        for (ContentPackagesConfigFields fields : dungeonPackages.values())
            fields.refreshDungeonVersionFromDisk();
        for (ContentPackagesConfigFields fields : enchantedChallengeDungeonPackages.values())
            fields.refreshDungeonVersionFromDisk();
    }

    public static void initializePackages() {
        for (ContentPackagesConfigFields fields : dungeonPackages.values())
            EMPackage.initialize(fields);
        for (ContentPackagesConfigFields fields : enchantedChallengeDungeonPackages.values())
            EMPackage.initialize(fields);
    }

}
