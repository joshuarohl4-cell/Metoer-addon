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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RedstoneESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Detection settings
    private final Setting<Boolean> detectObservers = sgGeneral.add(new BoolSetting.Builder()
        .name("observers")
        .description("Detect observers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectRepeaters = sgGeneral.add(new BoolSetting.Builder()
        .name("repeaters")
        .description("Detect repeaters")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectComparators = sgGeneral.add(new BoolSetting.Builder()
        .name("comparators")
        .description("Detect comparators")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectPistons = sgGeneral.add(new BoolSetting.Builder()
        .name("pistons")
        .description("Detect pistons and sticky pistons")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectHoppers = sgGeneral.add(new BoolSetting.Builder()
        .name("hoppers")
        .description("Detect hoppers (often used in farms)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectDroppers = sgGeneral.add(new BoolSetting.Builder()
        .name("droppers")
        .description("Detect droppers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectDispensers = sgGeneral.add(new BoolSetting.Builder()
        .name("dispensers")
        .description("Detect dispensers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectRails = sgGeneral.add(new BoolSetting.Builder()
        .name("rails")
        .description("Detect rails (suggests farms)")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectTNT = sgGeneral.add(new BoolSetting.Builder()
        .name("tnt")
        .description("Detect TNT")
        .defaultValue(true)
        .build()
    );

    // Render settings
    private final Setting<SettingColor> redstoneColor = sgRender.add(new ColorSetting.Builder()
        .name("redstone-color")
        .description("Color for redstone components")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private final Setting<SettingColor> pistonColor = sgRender.add(new ColorSetting.Builder()
        .name("piston-color")
        .description("Color for pistons")
        .defaultValue(new SettingColor(200, 150, 100, 50))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode for ESP boxes")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan")
        .defaultValue(-64)
        .min(-64)
        .max(256)
        .sliderRange(-64, 256)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan")
        .defaultValue(320)
        .min(-64)
        .max(320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Set<BlockPos> foundComponents = ConcurrentHashMap.newKeySet();

    public RedstoneESP() {
        super(AddonTemplate.CATEGORY, "redstone-esp", "Detects redstone components for finding farms.");
    }

    @Override
    public void onActivate() {
        foundComponents.clear();

        for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) {
                scanChunk(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        foundComponents.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof WorldChunk worldChunk) {
            scanChunk(worldChunk);
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null) return;

        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = minY.get(); y <= maxY.get(); y++) {
                    BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);
                    var state = chunk.getBlockState(pos);

                    if (isRedstoneComponent(state)) {
                        if (!foundComponents.contains(pos)) {
                            foundComponents.add(pos);
                        }
                    }
                }
            }
        }
    }

    private boolean isRedstoneComponent(net.minecraft.block.BlockState state) {
        var block = state.getBlock();

        if (detectObservers.get() && block == Blocks.OBSERVER) return true;
        if (detectRepeaters.get() && block == Blocks.REPEATER) return true;
        if (detectComparators.get() && block == Blocks.COMPARATOR) return true;
        if (detectPistons.get() && (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)) return true;
        if (detectHoppers.get() && block == Blocks.HOPPER) return true;
        if (detectDroppers.get() && block == Blocks.DROPPER) return true;
        if (detectDispensers.get() && block == Blocks.DISPENSER) return true;
        if (detectRails.get() && (block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL)) return true;
        if (detectTNT.get() && block == Blocks.TNT) return true;

        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        int renderDist = mc.options.getViewDistance().getValue() * 16;

        Color redstone = new Color(redstoneColor.get());
        Color piston = new Color(pistonColor.get());

        for (BlockPos pos : foundComponents) {
            double dx = camera.x - pos.getX() - 0.5;
            double dz = camera.z - pos.getZ() - 0.5;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > renderDist) continue;

            var state = mc.world.getBlockState(pos);
            Color color = (state.getBlock() == Blocks.PISTON || state.getBlock() == Blocks.STICKY_PISTON)
                ? piston : redstone;

            event.renderer.box(pos, color, color, shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(foundComponents.size());
    }
}
