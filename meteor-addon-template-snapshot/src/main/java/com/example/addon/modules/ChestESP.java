package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChestESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Detection settings
    private final Setting<Boolean> detectChests = sgGeneral.add(new BoolSetting.Builder()
        .name("chests")
        .description("Detect chests")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectBarrels = sgGeneral.add(new BoolSetting.Builder()
        .name("barrels")
        .description("Detect barrels")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectShulkerBoxes = sgGeneral.add(new BoolSetting.Builder()
        .name("shulker-boxes")
        .description("Detect shulker boxes")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chests")
        .description("Detect ender chests")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectHoppers = sgGeneral.add(new BoolSetting.Builder()
        .name("hoppers")
        .description("Detect hoppers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectFurnaces = sgGeneral.add(new BoolSetting.Builder()
        .name("furnaces")
        .description("Detect furnaces")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectDispensers = sgGeneral.add(new BoolSetting.Builder()
        .name("dispensers-droppers")
        .description("Detect dispensers and droppers")
        .defaultValue(false)
        .build()
    );

    // Render settings
    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder()
        .name("chest-color")
        .description("Color for chests")
        .defaultValue(new SettingColor(139, 69, 19, 50))
        .build()
    );

    private final Setting<SettingColor> barrelColor = sgRender.add(new ColorSetting.Builder()
        .name("barrel-color")
        .description("Color for barrels")
        .defaultValue(new SettingColor(139, 90, 43, 50))
        .build()
    );

    private final Setting<SettingColor> shulkerColor = sgRender.add(new ColorSetting.Builder()
        .name("shulker-color")
        .description("Color for shulker boxes")
        .defaultValue(new SettingColor(128, 0, 128, 50))
        .build()
    );

    private final Setting<SettingColor> enderChestColor = sgRender.add(new ColorSetting.Builder()
        .name("ender-chest-color")
        .description("Color for ender chests")
        .defaultValue(new SettingColor(75, 0, 130, 50))
        .build()
    );

    private final Setting<SettingColor> hopperColor = sgRender.add(new ColorSetting.Builder()
        .name("hopper-color")
        .description("Color for hoppers")
        .defaultValue(new SettingColor(100, 100, 100, 50))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode for ESP boxes")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> maxRender = sgRender.add(new IntSetting.Builder()
        .name("max-render")
        .description("Maximum number of blocks to render")
        .defaultValue(500)
        .min(10)
        .max(5000)
        .sliderRange(10, 5000)
        .build()
    );

    private final Set<BlockPos> foundChests = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, StorageType> storageTypes = new ConcurrentHashMap<>();

    public ChestESP() {
        super(AddonTemplate.CATEGORY, "chest-esp", "Detects and highlights chests and storage blocks.");
    }

    @Override
    public void onActivate() {
        foundChests.clear();
        storageTypes.clear();

        for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) {
                scanChunk(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        foundChests.clear();
        storageTypes.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof WorldChunk worldChunk) {
            scanChunk(worldChunk);
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null) return;

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockPos pos = blockEntity.getPos();
            StorageType type = getStorageType(blockEntity);

            if (type != null && !foundChests.contains(pos)) {
                foundChests.add(pos);
                storageTypes.put(pos, type);
            }
        }
    }

    private StorageType getStorageType(BlockEntity blockEntity) {
        if (detectChests.get() && blockEntity instanceof ChestBlockEntity) return StorageType.Chest;
        if (detectBarrels.get() && blockEntity instanceof BarrelBlockEntity) return StorageType.Barrel;
        if (detectShulkerBoxes.get() && blockEntity instanceof ShulkerBoxBlockEntity) return StorageType.ShulkerBox;
        if (detectEnderChests.get() && blockEntity instanceof EnderChestBlockEntity) return StorageType.EnderChest;
        if (detectHoppers.get() && blockEntity instanceof HopperBlockEntity) return StorageType.Hopper;
        if (detectFurnaces.get() && blockEntity instanceof AbstractFurnaceBlockEntity) return StorageType.Furnace;
        if (detectDispensers.get() && (blockEntity instanceof DispenserBlockEntity || blockEntity instanceof DropperBlockEntity)) return StorageType.Dispenser;
        return null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        int renderDist = mc.options.getViewDistance().getValue() * 16;
        int rendered = 0;

        for (BlockPos pos : foundChests) {
            if (rendered >= maxRender.get()) break;

            double dx = camera.x - pos.getX() - 0.5;
            double dz = camera.z - pos.getZ() - 0.5;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > renderDist) continue;

            StorageType type = storageTypes.get(pos);
            Color color = getColorForType(type);

            event.renderer.box(pos, color, color, shapeMode.get(), 0);
            rendered++;
        }
    }

    private Color getColorForType(StorageType type) {
        if (type == null) return new Color(chestColor.get());
        return switch (type) {
            case Chest -> new Color(chestColor.get());
            case Barrel -> new Color(barrelColor.get());
            case ShulkerBox -> new Color(shulkerColor.get());
            case EnderChest -> new Color(enderChestColor.get());
            case Hopper -> new Color(hopperColor.get());
            default -> new Color(100, 100, 100, 50);
        };
    }

    @Override
    public String getInfoString() {
        return String.valueOf(foundChests.size());
    }

    private enum StorageType {
        Chest, Barrel, ShulkerBox, EnderChest, Hopper, Furnace, Dispenser
    }
}
