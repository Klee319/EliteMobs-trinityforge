package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.items.ItemTagger;
import com.magmaguy.magmacore.util.ItemStackGenerator;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Disables repair of EliteMobs items when TrinityForge wants durability to behave as a finite resource
 * (fork spec section 8, "修繕廃止").
 * <p>
 * This listener covers the vanilla repair paths for elite items: anvil combination/repair (the anvil
 * result is blanked), the grindstone (combining two of the same elite item to restore durability is
 * blanked the same way — otherwise it would be the one durability-restoring path left unblocked), and
 * the Mending enchantment (the mend is cancelled so durability is not restored).
 * The EliteMobs custom scrap-repair path is neutralized at its single call site in {@code RepairMenu}
 * via {@link TrinityForgeIntegration#isRepairDisabled()} rather than from here, because that repair is
 * driven by a menu interaction rather than a cancellable durability event.
 * <p>
 * Everything is config-gated and TrinityForge-availability gated: with TrinityForge absent or the
 * {@code repair-disabled} toggle off, this listener is a no-op and vanilla EliteMobs repair is unchanged.
 */
public class TrinityForgeRepairListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!TrinityForgeIntegration.isRepairDisabled()) return;
        try {
            ItemStack first = event.getInventory().getItem(0);
            ItemStack second = event.getInventory().getItem(1);
            if (!ItemTagger.isEliteItem(first) && !ItemTagger.isEliteItem(second)) return;
            // null is the documented "no anvil output" sentinel; a synthetic AIR ItemStack can leave a
            // clickable empty result slot on some Paper builds.
            event.setResult(null);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge anvil repair block failed: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrindstonePrepare(PrepareGrindstoneEvent event) {
        if (!TrinityForgeIntegration.isRepairDisabled()) return;
        try {
            ItemStack first = event.getInventory().getItem(0);
            ItemStack second = event.getInventory().getItem(1);
            if (!ItemTagger.isEliteItem(first) && !ItemTagger.isEliteItem(second)) return;
            // Same null sentinel as the anvil block above: blanks the combined-durability result so
            // grindstone repair-by-combination cannot restore durability on elite items.
            event.setResult(null);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge grindstone repair block failed: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        if (!TrinityForgeIntegration.isRepairDisabled()) return;
        try {
            if (!ItemTagger.isEliteItem(event.getItem())) return;
            event.setCancelled(true);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge mending block failed: " + e.getMessage());
        }
    }
}
