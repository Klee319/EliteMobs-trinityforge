package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.pdc.BindType;
import com.trinityforge.pdc.ItemData;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Mirrors an EliteMobs soulbind into TrinityForge {@code ItemData} so TrinityForge observes the same
 * binding (fork spec section 8, "厳選完成品=SOULBOUND ... owner UUID").
 * <p>
 * EliteMobs stores its own soulbind in its private PDC keys; this bridge additionally writes the
 * TrinityForge bind type ({@link BindType#SOULBOUND}) and, when the owner is known, the owner UUID. It is
 * a small additive write: the EliteMobs binding itself is untouched, so removing TrinityForge leaves the
 * item working exactly as before.
 * <p>
 * The single intended call site is {@code SoulbindEnchantment.addEnchantment}. The method is fully guarded
 * and never throws: if TrinityForge is absent, the toggle is off, or the API errors, it is a no-op.
 */
public final class TrinityForgeBindingBridge {

    private TrinityForgeBindingBridge() {
    }

    /**
     * Writes {@link BindType#SOULBOUND} (and the owner, if non-null) onto the item's TrinityForge
     * {@code ItemData}. Mutates the passed {@link ItemMeta}; the caller is responsible for applying it
     * back onto the item with {@code setItemMeta} (which the existing call site already does).
     *
     * @param itemMeta the meta about to be soulbound by EliteMobs (must be non-null)
     * @param owner    the soulbind owner UUID, or {@code null} if no specific owner is known
     */
    public static void applySoulbound(ItemMeta itemMeta, UUID owner) {
        if (!TrinityForgeIntegration.isSoulbindBridgeEnabled()) return;
        if (itemMeta == null) return;
        try {
            ItemData itemData = ItemData.of(itemMeta);
            itemData.setBindType(BindType.SOULBOUND);
            if (owner != null) itemData.setOwner(owner);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge soulbind bridge failed: " + e.getMessage());
        }
    }
}
