package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockScan extends Module {
    private final SettingGroup sgScanner = settings.createGroup("Scanner");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    private final Setting<Integer> scanRange = sgScanner.add(new IntSetting.Builder()
        .name("scan-range")
        .description("Horizontal range to scan around player")
        .defaultValue(16)
        .min(4)
        .max(64)
        .sliderRange(4, 64)
        .build()
    );

    private final Setting<Integer> scanYMin = sgScanner.add(new IntSetting.Builder()
        .name("y-min")
        .description("Minimum Y level to scan (below bedrock)")
        .defaultValue(-60)
        .min(-64)
        .max(0)
        .sliderRange(-64, 0)
        .build()
    );

    private final Setting<Integer> scanYMax = sgScanner.add(new IntSetting.Builder()
        .name("y-max")
        .description("Maximum Y level to scan")
        .defaultValue(5)
        .min(-64)
        .max(20)
        .sliderRange(-64, 20)
        .build()
    );

    private final Setting<Integer> delayMin = sgScanner.add(new IntSetting.Builder()
        .name("delay-min")
        .description("Minimum delay between scans (ms)")
        .defaultValue(50)
        .min(10)
        .max(500)
        .sliderRange(10, 500)
        .build()
    );

    private final Setting<Integer> delayMax = sgScanner.add(new IntSetting.Builder()
        .name("delay-max")
        .description("Maximum delay between scans (ms)")
        .defaultValue(100)
        .min(10)
        .max(1000)
        .sliderRange(10, 1000)
        .build()
    );

    private final Setting<Boolean> showEsp = sgRender.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Show ESP boxes on found blocks")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder()
        .name("chest-color")
        .description("Color for chests")
        .defaultValue(new SettingColor(255, 165, 0, 100))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> spawnerColor = sgRender.add(new ColorSetting.Builder()
        .name("spawner-color")
        .description("Color for spawners")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> otherColor = sgRender.add(new ColorSetting.Builder()
        .name("other-color")
        .description("Color for other blocks")
        .defaultValue(new SettingColor(255, 0, 255, 100))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode for ESP")
        .defaultValue(ShapeMode.Both)
        .visible(showEsp::get)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracer lines")
        .defaultValue(true)
        .visible(showEsp::get)
        .build()
    );

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

    // Data storage
    private final Map<BlockPos, FoundBlock> foundBlocks = new ConcurrentHashMap<>();
    private final Set<BlockPos> scannedPositions = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();

    private Thread scanThread;
    private volatile boolean isScanning = true;
    private BlockPos lastScanned;
    private long nextScanTime = 0;
    private int blocksFound = 0;

    public BedrockScan() {
        super(AddonTemplate.CATEGORY, "bedrock-scan", "Scans below bedrock by sending probe packets to reveal hidden blocks.");
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
        scannedPositions.clear();
        scannedChunks.clear();
        blocksFound = 0;
        isScanning = true;

        scanThread = new Thread(this::scanLoop, "BedrockScan-Thread");
        scanThread.start();

        if (notifications.get()) {
            info("BedrockScan activated! Scanning for blocks below bedrock...");
        }
    }

    @Override
    public void onDeactivate() {
        isScanning = false;
        if (scanThread != null) {
            scanThread.interrupt();
            scanThread = null;
        }
        foundBlocks.clear();
        scannedPositions.clear();
    }

    private void scanLoop() {
        while (isScanning && !Thread.currentThread().isInterrupted()) {
            try {
                if (mc.player == null || mc.world == null) {
                    Thread.sleep(100);
                    continue;
                }

                long now = System.currentTimeMillis();
                if (now < nextScanTime) {
                    Thread.sleep(Math.min(10, nextScanTime - now));
                    continue;
                }

                BlockPos playerPos = mc.player.getBlockPos();
                int range = scanRange.get();

                // Generate random position in range
                int x = Utils.random(playerPos.getX() - range, playerPos.getX() + range);
                int y = Utils.random(scanYMin.get(), scanYMax.get());
                int z = Utils.random(playerPos.getZ() - range, playerPos.getZ() + range);

                BlockPos target = new BlockPos(x, y, z);

                // Check if already scanned
                if (!scannedPositions.contains(target)) {
                    scannedPositions.add(target);
                    lastScanned = target;

                    // Check if we can see this position
                    if (canSeePosition(target)) {
                        // Server should send us block update if there's a non-air block
                        ChunkPos chunkPos = new ChunkPos(target.getX() >> 4, target.getZ() >> 4);
                        if (!scannedChunks.contains(chunkPos)) {
                            scannedChunks.add(chunkPos);
                        }
                    } else {
                        // Send probe packet to force server to reveal
                        sendProbePacket(target);
                    }
                }

                // Set next scan time with random delay
                nextScanTime = now + Utils.random(delayMin.get(), delayMax.get());

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                // Log error but continue scanning
            }
        }
    }

    private boolean canSeePosition(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;

        // Simple distance check - if too far, can't see
        double dist = mc.player.getPos().distanceTo(new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        return dist < 10; // Within 10 blocks is visible
    }

    private void sendProbePacket(BlockPos pos) {
        if (mc.player == null) return;

        try {
            // Send START_DESTROY_BLOCK packet to probe
            PlayerActionC2SPacket startPacket = new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                pos,
                Direction.DOWN,
                0
            );
            mc.player.networkHandler.sendPacket(startPacket);

            // Send ABORT to cancel without breaking
            PlayerActionC2SPacket abortPacket = new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                pos,
                Direction.DOWN,
                0
            );
            mc.player.networkHandler.sendPacket(abortPacket);
        } catch (Exception e) {
            // Ignore packet errors
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Check for new block data from chunks
        int chunkX = (int) mc.player.getX() >> 4;
        int chunkZ = (int) mc.player.getZ() >> 4;
        int range = scanRange.get() >> 4;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                ChunkPos cp = new ChunkPos(chunkX + dx, chunkZ + dz);
                if (!scannedChunks.contains(cp)) {
                    scannedChunks.add(cp);
                    scanChunk(cp);
                }
            }
        }
    }

    private void scanChunk(ChunkPos cp) {
        if (mc.world == null) return;

        var chunk = mc.world.getChunk(cp.x, cp.z);
        if (!(chunk instanceof WorldChunk)) return;
        WorldChunk worldChunk = (WorldChunk) chunk;

        for (BlockEntity be : worldChunk.getBlockEntities().values()) {
            BlockPos pos = be.getPos();

            // Only interested in blocks below bedrock level
            if (pos.getY() > 0) continue;

            if (!foundBlocks.containsKey(pos)) {
                FoundBlock fb = new FoundBlock();
                fb.pos = pos;
                fb.type = getBlockType(be);
                foundBlocks.put(pos, fb);
                blocksFound++;

                if (notifications.get()) {
                    info("Found " + fb.type + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                }

                if (playSound.get()) {
                    mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                        net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f));
                }
            }
        }
    }

    private String getBlockType(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return "Chest";
        if (be instanceof MobSpawnerBlockEntity) return "Spawner";
        return be.getType().toString();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!showEsp.get() || mc.player == null) return;

        net.minecraft.util.math.Vec3d camera = mc.gameRenderer.getCamera().getPos();

        for (Map.Entry<BlockPos, FoundBlock> entry : foundBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            FoundBlock fb = entry.getValue();

            Color color = switch (fb.type) {
                case "Chest" -> new Color(chestColor.get());
                case "Spawner" -> new Color(spawnerColor.get());
                default -> new Color(otherColor.get());
            };

            // Draw box
            double x1 = pos.getX();
            double y1 = pos.getY();
            double z1 = pos.getZ();
            double x2 = x1 + 1;
            double y2 = y1 + 1;
            double z2 = z1 + 1;

            event.renderer.box(new Box(x1, y1, z1, x2, y2, z2), color, color, shapeMode.get(), 0);

            // Draw tracer
            if (tracers.get()) {
                event.renderer.line(camera.x, camera.y, camera.z, x1 + 0.5, y1 + 0.5, z1 + 0.5, color);
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        foundBlocks.clear();
        scannedPositions.clear();
        scannedChunks.clear();
        isScanning = false;
    }

    @Override
    public String getInfoString() {
        return blocksFound + " found";
    }

    private static class FoundBlock {
        BlockPos pos;
        String type;
    }
}
