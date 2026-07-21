package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class SusChunk extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    // Detection thresholds
    private final Setting<Integer> minChests = sgDetection.add(new IntSetting.Builder()
        .name("min-chests")
        .description("Minimum chests to flag chunk")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> minSpawners = sgDetection.add(new IntSetting.Builder()
        .name("min-spawners")
        .description("Minimum spawners to flag chunk")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> minHoppers = sgDetection.add(new IntSetting.Builder()
        .name("min-hoppers")
        .description("Minimum hoppers to flag chunk (mob farms)")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 50)
        .build()
    );

    private final Setting<Integer> minRedstone = sgDetection.add(new IntSetting.Builder()
        .name("min-redstone")
        .description("Minimum redstone components to flag chunk")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> detectCobblestone = sgDetection.add(new BoolSetting.Builder()
        .name("detect-cobblestone")
        .description("Flag chunks with lots of cobblestone (mining)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cobblestoneThreshold = sgDetection.add(new IntSetting.Builder()
        .name("cobblestone-threshold")
        .description("Cobblestone blocks to flag chunk")
        .defaultValue(50)
        .min(10)
        .sliderRange(10, 500)
        .visible(detectCobblestone::get)
        .build()
    );

    // Render settings
    private final Setting<SettingColor> baseColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-color")
        .description("Color for suspicious chunks")
        .defaultValue(new SettingColor(255, 255, 0, 30))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode for chunk highlights")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Double> renderHeight = sgRender.add(new DoubleSetting.Builder()
        .name("render-height")
        .description("Y level to render highlights")
        .defaultValue(64.0)
        .range(-64.0, 320.0)
        .sliderRange(-64.0, 320.0)
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
        .description("Play sound when suspicious chunk found")
        .defaultValue(true)
        .build()
    );

    // Data
    private final Map<ChunkPos, ChunkData> suspiciousChunks = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();

    public SusChunk() {
        super(AddonTemplate.CATEGORY, "sus-chunk", "Detects suspicious chunks for base finding.");
    }

    @Override
    public void onActivate() {
        suspiciousChunks.clear();
        scannedChunks.clear();
    }

    @Override
    public void onDeactivate() {
        suspiciousChunks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Periodic cleanup
        if (mc.player.age % 200 == 0) {
            cleanup();
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof WorldChunk worldChunk) {
            scanChunk(worldChunk);
        }
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        if (scannedChunks.contains(chunkPos)) return;
        scannedChunks.add(chunkPos);

        ChunkData data = new ChunkData();

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof ChestBlockEntity) data.chests++;
            else if (blockEntity instanceof MobSpawnerBlockEntity) data.spawners++;
            else if (blockEntity instanceof HopperBlockEntity) data.hoppers++;
            else if (blockEntity instanceof BarrelBlockEntity) data.chests++;
            else if (blockEntity instanceof ShulkerBoxBlockEntity) data.chests++;
            else if (blockEntity instanceof EnderChestBlockEntity) data.chests++;
            else if (blockEntity instanceof DropperBlockEntity) data.redstone++;
            else if (blockEntity instanceof DispenserBlockEntity) data.redstone++;
        }

        // Count redstone components
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = -64; y <= 320; y++) {
                    BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);
                    var state = chunk.getBlockState(pos);
                    var block = state.getBlock();

                    if (block == Blocks.OBSERVER || block == Blocks.REPEATER || block == Blocks.COMPARATOR) {
                        data.redstone++;
                    }
                    if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) {
                        data.redstone++;
                    }
                    if (detectCobblestone.get() && block == Blocks.COBBLESTONE) {
                        data.cobblestone++;
                    }
                }
            }
        }

        // Check if chunk is suspicious
        boolean isSuspicious = data.chests >= minChests.get() ||
            data.spawners >= minSpawners.get() ||
            data.hoppers >= minHoppers.get() ||
            data.redstone >= minRedstone.get() ||
            (detectCobblestone.get() && data.cobblestone >= cobblestoneThreshold.get());

        if (isSuspicious) {
            suspiciousChunks.put(chunkPos, data);

            if (notifications.get()) {
                String reason = getReason(data);
                info("§5[§dSusChunk§5] §bSuspicious chunk §5[§b" + chunkPos.x + ", " + chunkPos.z + "§5]§b - " + reason);
            }

            if (playSound.get()) {
                mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                    net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f));
            }
        }
    }

    private String getReason(ChunkData data) {
        List<String> reasons = new ArrayList<>();
        if (data.chests >= minChests.get()) reasons.add("Chests:" + data.chests);
        if (data.spawners >= minSpawners.get()) reasons.add("Spawners:" + data.spawners);
        if (data.hoppers >= minHoppers.get()) reasons.add("Hoppers:" + data.hoppers);
        if (data.redstone >= minRedstone.get()) reasons.add("Redstone:" + data.redstone);
        if (detectCobblestone.get() && data.cobblestone >= cobblestoneThreshold.get()) reasons.add("Cobble:" + data.cobblestone);
        return String.join(", ", reasons);
    }

    private void cleanup() {
        if (mc.player == null) return;

        int viewDist = mc.options.getViewDistance().getValue();
        int playerChunkX = (int) mc.player.getX() / 16;
        int playerChunkZ = (int) mc.player.getZ() / 16;

        suspiciousChunks.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            return Math.abs(pos.x - playerChunkX) > viewDist + 5 && Math.abs(pos.z - playerChunkZ) > viewDist + 5;
        });

        scannedChunks.removeIf(pos -> Math.abs(pos.x - playerChunkX) > viewDist + 3 && Math.abs(pos.z - playerChunkZ) > viewDist + 3);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (suspiciousChunks.isEmpty()) return;

        Color color = new Color(baseColor.get());
        double y = renderHeight.get();
        double h = 0.5;

        for (Map.Entry<ChunkPos, ChunkData> entry : suspiciousChunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            int startX = pos.getStartX();
            int startZ = pos.getStartZ();
            int endX = pos.getEndX() + 1;
            int endZ = pos.getEndZ() + 1;

            Box box = new Box(startX, y, startZ, endX, y + h, endZ);
            event.renderer.box(box, color, color, shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(suspiciousChunks.size());
    }

    private static class ChunkData {
        int chests = 0;
        int spawners = 0;
        int hoppers = 0;
        int redstone = 0;
        int cobblestone = 0;
    }
}
