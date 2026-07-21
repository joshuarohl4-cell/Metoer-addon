package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class BlockNotifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    // Block selection
    private final Setting<List<Block>> blocksToFind = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks-to-find")
        .description("Blocks to notify and highlight when found")
        .defaultValue(List.of(
            Blocks.SPAWNER,
            Blocks.CHEST,
            Blocks.ENDER_CHEST,
            Blocks.BARREL,
            Blocks.SHULKER_BOX
        ))
        .build()
    );

    // Render settings
    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color for ESP boxes")
        .defaultValue(new SettingColor(255, 165, 0, 50))
        .build()
    );

    private final Setting<SettingColor> outlineColor = sgRender.add(new ColorSetting.Builder()
        .name("outline-color")
        .description("Color for ESP outlines")
        .defaultValue(new SettingColor(255, 165, 0, 255))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode for ESP")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracer lines")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color for tracers")
        .defaultValue(new SettingColor(255, 165, 0, 200))
        .visible(tracers::get)
        .build()
    );

    // Notifications
    private final Setting<Boolean> notifications = sgNotifications.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> playSound = sgNotifications.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play sound on find")
        .defaultValue(true)
        .build()
    );

    // Data
    private final Set<BlockPos> foundBlocks = new HashSet<>();
    private final Set<ChunkPos> processedChunks = new HashSet<>();
    private int totalFound = 0;

    public BlockNotifier() {
        super(AddonTemplate.CATEGORY, "block-notifier", "Notifies and highlights specific blocks when found in loaded chunks.");
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
        processedChunks.clear();
        totalFound = 0;

        // Scan all loaded chunks
        if (mc.world != null && mc.player != null) {
            int chunkRadius = mc.options.getViewDistance().getValue();
            int playerChunkX = (int) mc.player.getX() >> 4;
            int playerChunkZ = (int) mc.player.getZ() >> 4;

            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    ChunkPos pos = new ChunkPos(playerChunkX + dx, playerChunkZ + dz);
                    if (mc.world.getChunkManager().isChunkLoaded(pos.x, pos.z)) {
                        var chunk = mc.world.getChunk(pos.x, pos.z);
                        if (chunk instanceof WorldChunk wChunk) {
                            scanChunk(wChunk);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        foundBlocks.clear();
        processedChunks.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof WorldChunk chunk) {
            scanChunk(chunk);
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null) return;

        ChunkPos chunkPos = chunk.getPos();
        if (processedChunks.contains(chunkPos)) return;
        processedChunks.add(chunkPos);

        List<Block> targetBlocks = blocksToFind.get();
        List<BlockPos> newFound = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = mc.world.getBottomY(); y < mc.world.getHeight(); y++) {
                    BlockPos pos = new BlockPos(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (targetBlocks.contains(block) && !foundBlocks.contains(pos)) {
                        foundBlocks.add(pos);
                        newFound.add(pos);
                        totalFound++;

                        if (notifications.get()) {
                            info("Found " + block.getName().getString() + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                        }
                    }
                }
            }
        }

        if (playSound.get() && !newFound.isEmpty() && mc.player != null) {
            mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f));
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        Color sideColor = new Color(espColor.get());
        Color outlineColorValue = new Color(outlineColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        int renderDist = mc.options.getViewDistance().getValue() * 16;

        for (BlockPos pos : foundBlocks) {
            double dx = camera.x - pos.getX() - 0.5;
            double dz = camera.z - pos.getZ() - 0.5;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > renderDist) continue;

            event.renderer.box(pos, sideColor, outlineColorValue, shapeMode.get(), 0);

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
