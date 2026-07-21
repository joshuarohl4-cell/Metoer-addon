package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StorageESP extends Module {
    // Detection settings
    private final Setting<Boolean> detectChests = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("chests")
        .description("Detect chests")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectSpawners = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("spawners")
        .description("Detect mob spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectShulkers = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("shulker-boxes")
        .description("Detect shulker boxes")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectBarrels = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("barrels")
        .description("Detect barrels")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectEnderChests = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("ender-chests")
        .description("Detect ender chests")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectHoppers = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("hoppers")
        .description("Detect hoppers")
        .defaultValue(false)
        .build()
    );

    // Render settings
    private final Setting<SettingColor> chestColor = settings.createGroup("Colors").add(new ColorSetting.Builder()
        .name("chest-color")
        .description("Color for chests")
        .defaultValue(new SettingColor(200, 150, 50, 80))
        .build()
    );

    private final Setting<SettingColor> spawnerColor = settings.createGroup("Colors").add(new ColorSetting.Builder()
        .name("spawner-color")
        .description("Color for spawners")
        .defaultValue(new SettingColor(0, 255, 0, 80))
        .build()
    );

    private final Setting<SettingColor> shulkerColor = settings.createGroup("Colors").add(new ColorSetting.Builder()
        .name("shulker-color")
        .description("Color for shulker boxes")
        .defaultValue(new SettingColor(180, 0, 180, 80))
        .build()
    );

    private final Setting<SettingColor> barrelColor = settings.createGroup("Colors").add(new ColorSetting.Builder()
        .name("barrel-color")
        .description("Color for barrels")
        .defaultValue(new SettingColor(150, 100, 50, 80))
        .build()
    );

    private final Setting<SettingColor> enderChestColor = settings.createGroup("Colors").add(new ColorSetting.Builder()
        .name("ender-chest-color")
        .description("Color for ender chests")
        .defaultValue(new SettingColor(75, 0, 130, 80))
        .build()
    );

    private final Setting<SettingColor> hopperColor = settings.createGroup("Colors").add(new ColorSetting.Builder()
        .name("hopper-color")
        .description("Color for hoppers")
        .defaultValue(new SettingColor(150, 150, 150, 80))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = settings.createGroup("Render").add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode for ESP")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = settings.createGroup("Render").add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracer lines")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxRender = settings.createGroup("Render").add(new IntSetting.Builder()
        .name("max-render")
        .description("Maximum blocks to render")
        .defaultValue(500)
        .min(10)
        .max(2000)
        .sliderRange(10, 2000)
        .build()
    );

    private final Setting<Boolean> notifications = settings.createGroup("Notifications").add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show notifications when blocks found")
        .defaultValue(true)
        .build()
    );

    // Storage for found blocks - NO PACKET SENDING
    private final Map<BlockPos, StorageBlock> foundBlocks = new ConcurrentHashMap<>();
    private final Set<ChunkPos> processedChunks = new HashSet<>();
    private int totalFound = 0;

    public StorageESP() {
        super(AddonTemplate.CATEGORY, "storage-esp", "Highlights storage blocks. Uses only data the server sends - no packet manipulation.");
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
        processedChunks.clear();
        totalFound = 0;

        // Scan all currently loaded chunks using what server already sent
        if (mc.world != null && mc.player != null) {
            int chunkX = (int) mc.player.getX() >> 4;
            int chunkZ = (int) mc.player.getZ() >> 4;
            int range = mc.options.getViewDistance().getValue();

            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    ChunkPos cp = new ChunkPos(chunkX + dx, chunkZ + dz);
                    scanChunk(cp);
                }
            }
        }

        if (notifications.get()) {
            info("StorageESP activated. Found " + totalFound + " storage blocks.");
        }
    }

    @Override
    public void onDeactivate() {
        foundBlocks.clear();
        processedChunks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        // Server is sending us chunk data - just read it
        if (event.chunk() instanceof WorldChunk worldChunk) {
            ChunkPos cp = worldChunk.getPos();
            if (!processedChunks.contains(cp)) {
                processedChunks.add(cp);
                scanChunkEntities(worldChunk);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        // Server is telling us about a block update - just read it
        BlockPos pos = event.pos;
        var block = event.newState.getBlock();

        if (isStorageBlock(block) && !foundBlocks.containsKey(pos)) {
            StorageType type = getStorageType(block);
            if (type != null) {
                StorageBlock sb = new StorageBlock();
                sb.pos = pos;
                sb.type = type;
                foundBlocks.put(pos, sb);
                totalFound++;

                if (notifications.get()) {
                    info("Found " + type + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                }
            }
        }
    }

    private void scanChunk(ChunkPos cp) {
        if (mc.world == null) return;
        if (!mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z)) return;

        var chunk = mc.world.getChunk(cp.x, cp.z);
        if (chunk instanceof WorldChunk worldChunk) {
            scanChunkEntities(worldChunk);
        }
    }

    private void scanChunkEntities(WorldChunk chunk) {
        // Read block entities from what server sent us
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockPos pos = be.getPos();
            if (foundBlocks.containsKey(pos)) continue;

            StorageType type = getBlockEntityType(be);
            if (type != null) {
                StorageBlock sb = new StorageBlock();
                sb.pos = pos;
                sb.type = type;
                foundBlocks.put(pos, sb);
                totalFound++;
            }
        }
    }

    private StorageType getBlockEntityType(BlockEntity be) {
        if (detectChests.get() && be instanceof ChestBlockEntity) return StorageType.Chest;
        if (detectSpawners.get() && be instanceof MobSpawnerBlockEntity) return StorageType.Spawner;
        if (detectShulkers.get() && be instanceof ShulkerBoxBlockEntity) return StorageType.ShulkerBox;
        if (detectBarrels.get() && be instanceof BarrelBlockEntity) return StorageType.Barrel;
        if (detectEnderChests.get() && be instanceof EnderChestBlockEntity) return StorageType.EnderChest;
        if (detectHoppers.get() && be instanceof HopperBlockEntity) return StorageType.Hopper;
        return null;
    }

    private boolean isStorageBlock(net.minecraft.block.Block block) {
        if (detectChests.get() && block == Blocks.CHEST) return true;
        if (detectSpawners.get() && block == Blocks.SPAWNER) return true;
        if (detectShulkers.get() && block == Blocks.SHULKER_BOX) return true;
        if (detectBarrels.get() && block == Blocks.BARREL) return true;
        if (detectEnderChests.get() && block == Blocks.ENDER_CHEST) return true;
        if (detectHoppers.get() && block == Blocks.HOPPER) return true;
        return false;
    }

    private StorageType getStorageType(net.minecraft.block.Block block) {
        if (block == Blocks.CHEST) return StorageType.Chest;
        if (block == Blocks.SPAWNER) return StorageType.Spawner;
        if (block == Blocks.SHULKER_BOX) return StorageType.ShulkerBox;
        if (block == Blocks.BARREL) return StorageType.Barrel;
        if (block == Blocks.ENDER_CHEST) return StorageType.EnderChest;
        if (block == Blocks.HOPPER) return StorageType.Hopper;
        return null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        int rendered = 0;
        int max = maxRender.get();

        for (Map.Entry<BlockPos, StorageBlock> entry : foundBlocks.entrySet()) {
            if (rendered >= max) break;

            BlockPos pos = entry.getKey();
            StorageBlock sb = entry.getValue();
            Color color = getColor(sb.type);

            // Distance check
            double dx = camera.x - pos.getX() - 0.5;
            double dz = camera.z - pos.getZ() - 0.5;
            double dist = Math.sqrt(dx * dx + dz * dz);
            int renderDist = mc.options.getViewDistance().getValue() * 16;
            if (dist > renderDist) continue;

            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            event.renderer.box(box, color, color, shapeMode.get(), 0);

            if (tracers.get()) {
                event.renderer.line(camera.x, camera.y, camera.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, color);
            }

            rendered++;
        }
    }

    private Color getColor(StorageType type) {
        return switch (type) {
            case Chest -> new Color(chestColor.get());
            case Spawner -> new Color(spawnerColor.get());
            case ShulkerBox -> new Color(shulkerColor.get());
            case Barrel -> new Color(barrelColor.get());
            case EnderChest -> new Color(enderChestColor.get());
            case Hopper -> new Color(hopperColor.get());
        };
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        foundBlocks.clear();
        processedChunks.clear();
    }

    @Override
    public String getInfoString() {
        return totalFound + " blocks";
    }

    private enum StorageType {
        Chest, Spawner, ShulkerBox, Barrel, EnderChest, Hopper
    }

    private static class StorageBlock {
        BlockPos pos;
        StorageType type;
    }
}
