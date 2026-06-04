package com.mcdg.game;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class HoleTeeMapManager {
    private static final String KEY_HOLE_MAP = "McdgHoleMap";
    private static final String KEY_HOLE_INDEX = "holeIndex";

    private HoleTeeMapManager() {
    }

    public static void ensureHoleMapForPlayer(ServerPlayerEntity player, int ordinal, BlockPos tee, BlockPos basket) {
        if (player == null || player.getWorld() == null || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (tee == null || basket == null || !isSupportedOrdinal(ordinal)) {
            return;
        }

        int existingSlot = findManagedHoleMapSlot(player);
        if (existingSlot >= 0) {
            ItemStack existing = player.getInventory().getStack(existingSlot);
            if (managedHoleIndex(existing) == ordinal) {
                return;
            }
        }

        ItemStack updatedMap = createHoleMap(world, ordinal, tee, basket);
        if (existingSlot >= 0) {
            player.getInventory().setStack(existingSlot, updatedMap);
            player.getInventory().markDirty();
            return;
        }

        int firstFree = firstFreeInventorySlot(player);
        if (firstFree >= 0) {
            player.getInventory().setStack(firstFree, updatedMap);
            player.getInventory().markDirty();
            return;
        }

        // Last-resort fallback when inventory is full.
        player.giveItemStack(updatedMap);
    }

    public static void clearManagedHoleMap(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        boolean removed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!isManagedHoleMap(stack)) {
                continue;
            }
            player.getInventory().setStack(slot, ItemStack.EMPTY);
            removed = true;
        }

        if (removed) {
            player.getInventory().markDirty();
        }
    }

    public static void clearAllRoundHoleMaps(MinecraftServer server) {
        if (server == null) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            clearManagedHoleMap(player);
        }
    }

    public static boolean isManagedHoleMap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) {
            return false;
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        NbtCompound customNbt = customData.copyNbt();
        return customNbt.contains(KEY_HOLE_MAP);
    }

    private static boolean isSupportedOrdinal(int ordinal) {
        return ordinal > 0;
    }

    private static ItemStack createHoleMap(ServerWorld world, int ordinal, BlockPos tee, BlockPos basket) {
        int centerX = Math.floorDiv(tee.getX() + basket.getX(), 2);
        int centerZ = Math.floorDiv(tee.getZ() + basket.getZ(), 2);

        int deltaX = Math.abs(tee.getX() - basket.getX());
        int deltaZ = Math.abs(tee.getZ() - basket.getZ());
        int maxAxisDistance = Math.max(deltaX, deltaZ);
        int halfSpan = Math.max(16, (int) Math.ceil(maxAxisDistance / 2.0) + 12);
        byte scale = 0;
        while (scale < 4 && ((64 << scale) < halfSpan)) {
            scale++;
        }

        ItemStack map = FilledMapItem.createMap(world, centerX, centerZ, scale, true, false);
        map.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Hole " + ordinal + " Map"));
        MapState mapState = FilledMapItem.getMapState(map, world);
        if (mapState != null) {
            preRenderHoleMap(world, mapState);
            mapState.markDirty();
        }
        MapState.addDecorationsNbt(map, tee, "mcdg_tee", MapDecorationTypes.BLUE_MARKER);
        MapState.addDecorationsNbt(map, basket, "mcdg_basket", MapDecorationTypes.TARGET_X);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, map, nbt -> {
            NbtCompound root = new NbtCompound();
            root.putInt(KEY_HOLE_INDEX, ordinal);
            nbt.put(KEY_HOLE_MAP, root);
        });
        return map;
    }

    private static void preRenderHoleMap(ServerWorld world, MapState mapState) {
        if (world == null || mapState == null) {
            return;
        }

        int step = 1 << mapState.scale;
        for (int mapZ = 0; mapZ < 128; mapZ++) {
            for (int mapX = 0; mapX < 128; mapX++) {
                int worldX = mapState.centerX + ((mapX - 64) * step);
                int worldZ = mapState.centerZ + ((mapZ - 64) * step);
                byte color = sampleMapColor(world, worldX, worldZ);
                mapState.setColor(mapX, mapZ, color);
            }
        }
    }

    private static byte sampleMapColor(ServerWorld world, int worldX, int worldZ) {
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, worldX, worldZ) - 1;
        BlockPos surfacePos = new BlockPos(worldX, surfaceY, worldZ);
        BlockState surfaceState = world.getBlockState(surfacePos);
        MapColor mapColor = surfaceState.getMapColor(world, surfacePos);
        if (mapColor == null) {
            mapColor = MapColor.CLEAR;
        }
        return mapColor.getRenderColorByte(MapColor.Brightness.NORMAL);
    }

    private static int findManagedHoleMapSlot(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isManagedHoleMap(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static int firstFreeInventorySlot(ServerPlayerEntity player) {
        // Keep managed round maps out of the hotbar so throw-item slots remain stable.
        for (int slot = 9; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int managedHoleIndex(ItemStack stack) {
        if (!isManagedHoleMap(stack)) {
            return -1;
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return -1;
        }
        NbtCompound customNbt = customData.copyNbt();
        if (!customNbt.contains(KEY_HOLE_MAP)) {
            return -1;
        }
        return customNbt.getCompound(KEY_HOLE_MAP).getInt(KEY_HOLE_INDEX);
    }
}