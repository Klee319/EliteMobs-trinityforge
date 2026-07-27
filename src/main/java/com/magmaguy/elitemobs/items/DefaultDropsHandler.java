package com.magmaguy.elitemobs.items;

import com.magmaguy.elitemobs.api.EliteMobDeathEvent;
import com.magmaguy.elitemobs.config.ItemSettingsConfig;
import com.magmaguy.elitemobs.trinityforge.TrinityForgeIntegration;
import com.trinityforge.config.ConfigManager;
import com.trinityforge.pdc.MobData;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created by MagmaGuy on 04/06/2017.
 */
public class DefaultDropsHandler implements Listener {

    private final List<ItemStack> wornItems = new ArrayList<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EliteMobDeathEvent event) {

        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (!event.getEliteEntity().isVanillaLoot()) return;
        if (event.getEntityDeathEvent() == null) return;

        List<ItemStack> droppedItems = event.getEntityDeathEvent().getDrops();
        int mobLevel = event.getEliteEntity().getLevel();

        if (mobLevel > ItemSettingsConfig.getMaxLevelForDefaultLootMultiplier())
            mobLevel = ItemSettingsConfig.getMaxLevelForDefaultLootMultiplier();

        inventoryItemsConstructor((LivingEntity) event.getEntity());

        if (ItemSettingsConfig.getDefaultLootMultiplier() != 0) {
            for (ItemStack itemStack : droppedItems) {

                if (itemStack == null) continue;
                if (itemStack.getType().equals(Material.AIR)) continue;
                boolean itemIsWorn = false;

                for (ItemStack wornItem : wornItems)
                    if (wornItem.isSimilar(itemStack))
                        itemIsWorn = true;

                if (!itemIsWorn)
                    for (int i = 0; i < mobLevel * 0.1 * ItemSettingsConfig.getDefaultLootMultiplier(); i++)
                        event.getEntity().getLocation().getWorld().dropItem(event.getEntity().getLocation(), itemStack);

            }
        }

        mobLevel = (int) (event.getEliteEntity().getLevel() * ItemSettingsConfig.getDefaultExperienceMultiplier());

        // 2026-07-26 TrinityForge combat/mob-overrides.yml H1修正(バニラEXP二重付与):
        // このモブに対してTrinityForgeが vanilla-exp ランプを設定済みの場合、EXPの最終決定権は
        // TrinityForge側(MobOverrideExpListener, MONITOR優先度で同じEntityDeathEventに書き込む)に
        // 完全に譲る。ここで従来通り自前のExperienceOrbを飛ばしてdroppedExpを0にすると、その後
        // TrinityForge側がdroppedExpをランプ値で再設定した時点でバニラの死亡後XP処理と二重払いに
        // なる(実サーバのボス408体中 dropsVanillaLoot:true/キー無し(既定true)の159体で確認済み)。
        // ランプが設定されていないモブ(mob-overrides.ymlに記載がない/dropsVanillaLoot:falseで
        // そもそもこのブロックへ来ない)は、従来通りこのハンドラがEXPを完全に握る — 挙動維持。
        if (trinityForgeOwnsVanillaExp(event)) {
            return;
        }

        int droppedXP = (int) (event.getEntityDeathEvent().getDroppedExp() + event.getEntityDeathEvent().getDroppedExp() * 0.1 * mobLevel);
        event.getEntityDeathEvent().setDroppedExp(0);
        event.getEntity().getWorld().spawn(event.getEntity().getLocation(), ExperienceOrb.class).setExperience(droppedXP);

    }

    /**
     * True when TrinityForgeの{@code combat/mob-overrides.yml}がこのモブ(死亡ワールド×PDCスタンプ済み
     * MOB_PROFILE_ID×スタンプ済みレベル)に対して{@code vanilla-exp}ランプを設定している。TrinityForge未
     * 導入/未スタンプ/ランプ未設定のいずれでも{@code false}を返し(fail-open)、その場合はこのハンドラが
     * 従来通りEXPを処理する。
     */
    private static boolean trinityForgeOwnsVanillaExp(EliteMobDeathEvent event) {
        if (!TrinityForgeIntegration.isAvailable()) return false;
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return false;
        ConfigManager config = TrinityForgeIntegration.config();
        if (config == null) return false;
        MobData mobData = MobData.of(livingEntity);
        Optional<String> profileId = mobData.profileId();
        if (profileId.isEmpty()) return false;
        try {
            return config.mobOverrides()
                    .vanillaExpFor(livingEntity.getWorld().getName(), profileId.get(), mobData.level())
                    .isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }


    private List<ItemStack> inventoryItemsConstructor(LivingEntity entity) {

        EntityEquipment equipment = entity.getEquipment();

        if (equipment.getItemInMainHand() != null && !equipment.getItemInMainHand().getType().equals(Material.AIR))
            wornItems.add(equipment.getItemInMainHand());

        if (equipment.getHelmet() != null)
            wornItems.add(equipment.getHelmet());

        if (equipment.getChestplate() != null)
            wornItems.add(equipment.getChestplate());

        if (equipment.getLeggings() != null)
            wornItems.add(equipment.getLeggings());

        if (equipment.getBoots() != null)
            wornItems.add(equipment.getBoots());

        return wornItems;

    }

}
