package com.magmaguy.elitemobs.items.customenchantments;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.config.enchantments.EnchantmentsConfig;
import com.magmaguy.elitemobs.items.ItemTagger;
import com.magmaguy.elitemobs.utils.EventCaller;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DrillingEnchantment extends CustomEnchantment {

    public static String key = "drilling";

    public DrillingEnchantment() {
        super(key, false);
    }

    public static void shutdown() {
        DrillingEnchantmentEvents.shutdown();
    }

    public static class DrillingEnchantmentEvents implements Listener {
        private static final Set<UUID> activePlayers = new HashSet<>();

        public static void shutdown() {
            activePlayers.clear();
        }
        private Material material = null;
        private ItemStack itemStack = null;
        private MiningDirection miningDirection = null;
        private Player player;

        /**
         * この1回のドリリングで実際に壊した座標。
         *
         * <p>{@link #resendDrilledBlocks(Player)} でクライアントへ送り直すために貯める。
         * 他のフィールドと同じく、{@code drillBlocks} が1イベント内で同期に走りきる前提で使い回している
         * （{@code activePlayers} が再入を止めているので、掘っている最中に別の掘削が挟まることはない）。
         */
        private final List<Location> drilledLocations = new ArrayList<>();

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDig(BlockBreakEvent event) {
            if (event.isCancelled()) return;
            if (!event.getPlayer().getInventory().getItemInMainHand().hasItemMeta() ||
                    event.getPlayer().getInventory().getItemInMainHand().getItemMeta() == null) return;
            if (!ItemTagger.hasEnchantment(event.getPlayer().getInventory().getItemInMainHand().getItemMeta(), new NamespacedKey(MetadataHandler.PLUGIN, key)))
                return;
            if (event.getPlayer().isSneaking()) return;
            if (!EnchantmentsConfig.getEnchantment("drilling.yml").isEnabled()) return;
            if (activePlayers.contains(event.getPlayer().getUniqueId())) return;

            drillBlocks(event.getBlock(),
                    ItemTagger.getEnchantment(event.getPlayer().getInventory().getItemInMainHand().getItemMeta(), new NamespacedKey(MetadataHandler.PLUGIN, key)),
                    event.getPlayer().getLocation(),
                    event.getPlayer().getInventory().getItemInMainHand(),
                    event.getPlayer());

        }

        private void drillBlocks(Block originalBlock, int enchantmentLevel, Location playerLocation, ItemStack playerItem, Player player) {

            this.player = player;
            this.material = originalBlock.getType();
            this.itemStack = playerItem;
            this.miningDirection = determineDirection(originalBlock.getLocation(), playerLocation);
            this.drilledLocations.clear();

            activePlayers.add(player.getUniqueId());

            switch (enchantmentLevel) {
                case 1:
                    drillLevel1(originalBlock);
                    break;
                case 2:
                    drillLevel2(originalBlock);
                    break;
                case 3:
                    drillLevel3(originalBlock);
                    drillLevel2(drillLevel1(originalBlock));
                    break;
                case 4:
                    drillLevel3(originalBlock);
                    drillLevel3(drillLevel1(originalBlock));
                    drillLevel2(drillLevel1(drillLevel1(originalBlock)));
                    break;
                case 5:
                default:
                    drillLevel3(originalBlock);
                    drillLevel3(drillLevel1(originalBlock));
                    drillLevel3(drillLevel1(drillLevel1(originalBlock)));
            }

            activePlayers.remove(player.getUniqueId());

            resendDrilledBlocks(player);

        }

        /**
         * サーバが消したブロックを、掘ったプレイヤーへもう一度送り直す（2026-08-25）。
         *
         * <p><b>直している症状</b>: ドリリングで掘ったあと、消えたはずのブロックが画面に残り、
         * 叩いても何も起きない（{@code BlockBreakEvent} すら飛ばない）。F3+A のチャンク再描画で
         * 消えるので、サーバ側は air なのに<b>クライアントだけがブロックを持ったまま</b>＝ゴーストブロック。
         *
         * <p><b>なぜ起きるか</b>: {@code breakNaturally} 自体はブロック更新を送るが、
         * 掘っている本人のクライアントは「今まさに壊しているブロック」を自前で先読み描画しており、
         * 1回の {@code BlockBreakEvent} で周囲を最大 27 マスまとめて消す Lv4/5 では、
         * その先読みと後から届く更新が食い違って取りこぼしが出る。
         *
         * <p><b>なぜ 2 tick 後か</b>: 同じ tick に送り返すと、クライアントの破壊予測が
         * 後からその上に乗ってしまい、また同じ絵に戻る。予測が落ち着いてから送る。
         *
         * <p><b>なぜ掘った本人にだけ送るか</b>: 食い違うのは破壊予測を持っている本人だけで、
         * 周りのプレイヤーは通常のブロック更新をそのまま受け取っている。全員へ送ると
         * Lv5 の連打で無駄なパケットが跳ね上がる。
         */
        private void resendDrilledBlocks(Player miner) {
            if (drilledLocations.isEmpty()) return;
            List<Location> snapshot = new ArrayList<>(drilledLocations);
            drilledLocations.clear();
            Bukkit.getScheduler().runTaskLater(MetadataHandler.PLUGIN, () -> {
                if (!miner.isOnline()) return;
                for (Location location : snapshot) {
                    // 送り直すのは「今のサーバ側の状態」。air とは限らない
                    // (掘った直後に水が流れ込む・砂が落ちてくる、といったことが起きる)。
                    miner.sendBlockChange(location, location.getBlock().getBlockData());
                }
            }, 2L);
        }

        private MiningDirection determineDirection(Location blockLocation, Location playerLocation) {

            Location adjustedPlayerLocation = playerLocation.clone().add(new Vector(0, 1, 0));
            Location adjustedBlockLocation = blockLocation.clone().add(new Vector(0.5, 0.5, 0.5));

            Vector directionVector = adjustedBlockLocation.clone().subtract(adjustedPlayerLocation).toVector().normalize();

            double x = directionVector.getX();
            double y = directionVector.getY();
            double z = directionVector.getZ();

            if (Math.abs(y) > 0.9) {
                if (y > 0)
                    return MiningDirection.UP;
                else
                    return MiningDirection.DOWN;
            }

            if (Math.abs(x) > Math.abs(z)) {
                if (x > 0)
                    return MiningDirection.EAST;
                else
                    return MiningDirection.WEST;
            }

            if (z > 0)
                return MiningDirection.NORTH;

            return MiningDirection.SOUTH;

        }

        private Block processBlock(Block originalBlock, Vector addedVector) {
            if (originalBlock == null) return null;
            Block finalBlock = originalBlock.getWorld().getBlockAt(originalBlock.getLocation().clone().add(addedVector));
            if (!this.material.equals(finalBlock.getType())) return finalBlock;

            BlockBreakEvent blockBreakEvent = new BlockBreakEvent(finalBlock, player);
            new EventCaller(blockBreakEvent);
            if (blockBreakEvent.isCancelled()) return null;

            finalBlock.breakNaturally(this.itemStack);
            // 消した座標を控える。あとで掘った本人へ送り直さないとゴーストブロックが残る
            // (理由は resendDrilledBlocks の javadoc)。
            drilledLocations.add(finalBlock.getLocation());
            return finalBlock;
        }

        private Block drillLevel1(Block originalBlock) {

            switch (miningDirection) {
                case NORTH:
                    return processBlock(originalBlock, new Vector(0, 0, 1));
                case SOUTH:
                    return processBlock(originalBlock, new Vector(0, 0, -1));
                case EAST:
                    return processBlock(originalBlock, new Vector(1, 0, 0));
                case WEST:
                    return processBlock(originalBlock, new Vector(-1, 0, 0));
                case UP:
                    return processBlock(originalBlock, new Vector(0, 1, 0));
                case DOWN:
                    return processBlock(originalBlock, new Vector(0, -1, 0));
                default:
                    return null;
            }

        }

        private void drillLevel2(Block originalBlock) {

            switch (miningDirection) {
                case NORTH:
                case SOUTH:
                    processBlock(originalBlock, new Vector(0, 1, 0));
                    processBlock(originalBlock, new Vector(0, -1, 0));
                    processBlock(originalBlock, new Vector(1, 0, 0));
                    processBlock(originalBlock, new Vector(-1, 0, 0));
                    break;
                case EAST:
                case WEST:
                    processBlock(originalBlock, new Vector(0, 1, 0));
                    processBlock(originalBlock, new Vector(0, -1, 0));
                    processBlock(originalBlock, new Vector(0, 0, 1));
                    processBlock(originalBlock, new Vector(0, 0, -1));
                    break;
                case UP:
                case DOWN:
                    processBlock(originalBlock, new Vector(1, 0, 0));
                    processBlock(originalBlock, new Vector(-1, 0, 0));
                    processBlock(originalBlock, new Vector(0, 0, 1));
                    processBlock(originalBlock, new Vector(0, 0, -1));
                    break;
            }

        }

        private void drillLevel3(Block originalBlock) {

            switch (miningDirection) {
                case NORTH:
                case SOUTH:
                    for (int x = -1; x < 2; x++)
                        for (int y = -1; y < 2; y++)
                            if (!(x == 0 && y == 0))
                                processBlock(originalBlock, new Vector(x, y, 0));
                    break;
                case EAST:
                case WEST:
                    for (int y = -1; y < 2; y++)
                        for (int z = -1; z < 2; z++)
                            if (!(y == 0 && z == 0))
                                processBlock(originalBlock, new Vector(0, y, z));
                    break;
                case UP:
                case DOWN:
                    for (int x = -1; x < 2; x++)
                        for (int z = -1; z < 2; z++)
                            if (!(x == 0 && z == 0))
                                processBlock(originalBlock, new Vector(x, 0, z));
                    break;
            }

        }

        private enum MiningDirection {
            UP,
            DOWN,
            NORTH,
            SOUTH,
            EAST,
            WEST
        }
    }

}
