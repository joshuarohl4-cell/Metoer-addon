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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<SettingColor> spawnerColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-color")
        .description("Color for spawner ESP boxes")
        .defaultValue(new SettingColor(0, 255, 0, 50))
        .build()
    );

    private final Setting<SettingColor> spawnerOutline = sgRender.add(new ColorSetting.Builder()
        .name("spawner-outline")
        .description("Color for spawner ESP outlines")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode for ESP boxes")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracer lines to spawners")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color for tracer lines")
        .defaultValue(new SettingColor(0, 255, 0, 200))
        .visible(tracers::get)
        .build()
    );

    private final Set<BlockPos> foundSpawners = ConcurrentHashMap.newKeySet();
    private int totalFound = 0;

    public SpawnerESP() {
        super(AddonTemplate.CATEGORY, "spawner-esp", "Highlights spawners with green ESP boxes. Note: Only works when spawners are visible (not behind walls on anti-xray servers).");
    }

    @Override
    public void onActivate() {
        foundSpawners.clear();
        totalFound = 0;

        for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) {
                scanChunk(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        foundSpawners.clear();
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
            if (blockEntity instanceof MobSpawnerBlockEntity) {
                BlockPos pos = blockEntity.getPos();
                if (!foundSpawners.contains(pos)) {
                    foundSpawners.add(pos);
                    totalFound++;
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        Color sideColor = new Color(spawnerColor.get());
        Color outlineColor = new Color(spawnerOutline.get());
        Color tracerColorValue = new Color(tracerColor.get());

        int renderDist = mc.options.getViewDistance().getValue() * 16;

        for (BlockPos pos : foundSpawners) {
            double dx = camera.x - pos.getX() - 0.5;
            double dz = camera.z - pos.getZ() - 0.5;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > renderDist) continue;

            event.renderer.box(pos, sideColor, outlineColor, shapeMode.get(), 0);

            if (tracers.get()) {
                Vec3d center = Vec3d.ofCenter(pos);
                event.renderer.line(camera.x, camera.y, camera.z, center.x, center.y, center.z, tracerColorValue);
            }
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(totalFound);
    }
}
