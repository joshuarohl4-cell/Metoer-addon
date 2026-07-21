package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkFinder extends Module {
    public enum Mode {
        Chat,
        Toast,
        Both
    }

    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgBlockHighlight = settings.createGroup("Block Highlighting");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    private final Setting<Boolean> detectDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-deepslate")
        .description("Find deepslate blocks")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> detectCobbledDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-cobbled-deepslate")
        .description("Find cobbled deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectRotatedDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Find rotated deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectEndStone = sgDetection.add(new BoolSetting.Builder()
        .name("detect-end-stone")
        .description("Find end stone blocks (disabled in The End dimension)")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreExposed = sgDetection.add(new BoolSetting.Builder()
        .name("ignore-exposed")
        .description("Ignore suspicious blocks that are exposed to air or fluid")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreTrialChambers = sgDetection.add(new BoolSetting.Builder()
        .name("ignore-trial-chambers")
        .description("Ignore chunks containing trial chambers")
        .defaultValue(true)
        .build());

    private final Setting<Integer> cobbledDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("cobbled-threshold")
        .description("Min cobbled deepslate to flag chunk")
        .defaultValue(4)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectCobbledDeepslate::get)
        .build());

    private final Setting<Integer> rotatedDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("rotated-threshold")
        .description("Min rotated deepslate to flag chunk")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(detectRotatedDeepslate::get)
        .build());

    private final Setting<Integer> endStoneThreshold = sgDetection.add(new IntSetting.Builder()
        .name("end-stone-threshold")
        .description("Min end stone count to flag chunk")
        .defaultValue(2)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectEndStone::get)
        .build());

    private final Setting<Double> renderY = sgRender.add(new DoubleSetting.Builder()
        .name("render-height")
        .description("Height to render chunk highlights")
        .defaultValue(64.0)
        .range(-64.0, 320.0)
        .sliderRange(-64.0, 320.0)
        .build());
    
    private final Setting<ShapeMode> renderMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("render-mode")
        .description("How to render highlighted chunks")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> chunkColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-color")
        .description("Color for suspicious chunks")
        .defaultValue(new SettingColor(255, 215, 0, 120))
        .build());

    private final Setting<Boolean> highlightBlocks = sgBlockHighlight.add(new BoolSetting.Builder()
        .name("highlight-blocks")
        .description("Highlight individual suspicious blocks")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxBlocksToRender = sgBlockHighlight.add(new IntSetting.Builder()
        .name("max-blocks-render")
        .description("Maximum number of blocks to highlight")
        .defaultValue(200)
        .range(50, 1000)
        .sliderRange(50, 1000)
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> cobbledDeepslateColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("cobbled-color")
        .description("Color for cobbled deepslate blocks")
        .defaultValue(new SettingColor(128, 128, 128, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> rotatedDeepslateColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("rotated-deepslate-color")
        .description("Color for rotated deepslate blocks")
        .defaultValue(new SettingColor(100, 100, 100, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> endStoneColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("end-stone-color")
        .description("Color for end stone blocks")
        .defaultValue(new SettingColor(230, 240, 100, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<Boolean> useThreading = sgPerformance.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for scanning")
        .defaultValue(true)
        .build());

    private final Setting<Integer> threadPoolSize = sgPerformance.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads for scanning")
        .defaultValue(4)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build());

    private final Setting<Mode> notificationMode = sgNotifications.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when suspicious chunk is found")
        .defaultValue(Mode.Both)
        .build());

    private final Setting<Boolean> chatAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Show alerts in chat")
        .defaultValue(true)
        .visible(() -> notificationMode.get() == Mode.Chat || notificationMode.get() == Mode.Both)
        .build());

    private final Setting<Boolean> playSound = sgNotifications.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play sound when suspicious chunk is found")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxAlerts = sgNotifications.add(new IntSetting.Builder()
        .name("max-alerts")
        .description("Maximum number of alerts to keep in memory")
        .defaultValue(100)
        .range(10, 500)
        .sliderRange(10, 500)
        .build());

    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, ChunkAnalysis> chunkData = new ConcurrentHashMap<>();
    private final Map<BlockPos, SuspiciousBlock> suspiciousBlocks = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Long> notificationTimes = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final AtomicLong scanStartTime = new AtomicLong(0);

    private ExecutorService threadPool;

    public ChunkFinder() {
        super(AddonTemplate.CATEGORY, "chunk-finder", "Finds suspicious chunks for base finding on Donut SMP.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        flaggedChunks.clear();
        scannedChunks.clear();
        chunkData.clear();
        suspiciousBlocks.clear();
        notificationTimes.clear();
        recentAlerts.clear();
        scanStartTime.set(System.currentTimeMillis());

        for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) {
                scanChunk(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
            threadPool = null;
        }

        flaggedChunks.clear();
        scannedChunks.clear();
        chunkData.clear();
        suspiciousBlocks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Cleanup old data periodically
        if (mc.player.age % 100 == 0) {
            performCleanup();
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof WorldChunk worldChunk) {
            scanChunk(worldChunk);
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        ChunkPos chunkPos = new ChunkPos(pos);
        net.minecraft.world.chunk.Chunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        if (chunk instanceof WorldChunk worldChunk) {
            scanChunk(worldChunk);
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null || chunk == null) return;

        ChunkPos chunkPos = chunk.getPos();

        if (scannedChunks.contains(chunkPos)) return;
        scannedChunks.add(chunkPos);

        ChunkAnalysis analysis = analyzeChunk(chunk);
        chunkData.put(chunkPos, analysis);

        if (isSuspicious(analysis)) {
            flaggedChunks.add(chunkPos);
            notifyChunkFound(chunkPos, analysis);
        }
    }

    private ChunkAnalysis analyzeChunk(WorldChunk chunk) {
        ChunkAnalysis analysis = new ChunkAnalysis();
        ChunkPos chunkPos = chunk.getPos();
        int baseX = chunkPos.getStartX();
        int baseZ = chunkPos.getStartZ();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = -64; y <= 320; y++) {
                    BlockPos pos = new BlockPos(baseX + dx, y, baseZ + dz);
                    BlockState state = chunk.getBlockState(pos);

                    if (isCobbledDeepslate(state)) {
                        analysis.cobbledDeepslateCount++;
                        addSuspiciousBlock(pos, SuspiciousBlockType.COBBLED_DEEPSLATE);
                    }
                    if (isRotatedDeepslate(state)) {
                        analysis.rotatedDeepslateCount++;
                        addSuspiciousBlock(pos, SuspiciousBlockType.ROTATED_DEEPSLATE);
                    }
                    if (isEndStone(state)) {
                        analysis.endStoneCount++;
                        addSuspiciousBlock(pos, SuspiciousBlockType.END_STONE);
                    }
                }
            }
        }

        return analysis;
    }

    private void addSuspiciousBlock(BlockPos pos, SuspiciousBlockType type) {
        if (!ignoreExposed.get() || !isExposed(pos)) {
            suspiciousBlocks.put(pos, new SuspiciousBlock(type, System.currentTimeMillis()));
        }
    }

    private boolean isExposed(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isSuspicious(ChunkAnalysis analysis) {
        if (ignoreTrialChambers.get() && analysis.trialChamberCount > 50) {
            return false;
        }

        boolean hasSuspiciousBlock = (detectCobbledDeepslate.get() && analysis.cobbledDeepslateCount >= cobbledDeepslateThreshold.get()) ||
            (detectRotatedDeepslate.get() && analysis.rotatedDeepslateCount >= rotatedDeepslateThreshold.get()) ||
            (detectEndStone.get() && analysis.endStoneCount >= endStoneThreshold.get());

        return hasSuspiciousBlock;
    }

    private void notifyChunkFound(ChunkPos pos, ChunkAnalysis analysis) {
        long now = System.currentTimeMillis();

        if (recentAlerts.size() >= maxAlerts.get()) return;

        Long lastNotification = notificationTimes.get(pos);
        if (lastNotification != null && now - lastNotification < 45000) return;

        StringBuilder details = new StringBuilder();
        if (analysis.cobbledDeepslateCount > 0) details.append("Cobbled:").append(analysis.cobbledDeepslateCount).append(" ");
        if (analysis.rotatedDeepslateCount > 0) details.append("Rotated:").append(analysis.rotatedDeepslateCount).append(" ");
        if (analysis.endStoneCount > 0) details.append("EndStone:").append(analysis.endStoneCount);

        String message = String.format("§5[§dChunkFinder§5] §b[%d, %d]§5 - %s", pos.x, pos.z, details.toString().trim());

        mc.execute(() -> {
            // Always send chat message
            if (chatAlerts.get() && mc.player != null) {
                mc.player.sendMessage(Text.literal(message), false);
            }

            if (playSound.get()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
            }

            recentAlerts.offer(now);
            notificationTimes.put(pos, now);
        });
    }

    private void performCleanup() {
        if (mc.player == null) return;

        int viewDist = mc.options.getViewDistance().getValue();
        int playerChunkX = (int) mc.player.getX() / 16;
        int playerChunkZ = (int) mc.player.getZ() / 16;

        flaggedChunks.removeIf(pos -> {
            int dx = Math.abs(pos.x - playerChunkX);
            int dz = Math.abs(pos.z - playerChunkZ);
            boolean tooFar = dx > viewDist + 5 || dz > viewDist + 5;

            if (tooFar) {
                chunkData.remove(pos);
                notificationTimes.remove(pos);
            }
            return tooFar;
        });

        scannedChunks.removeIf(pos -> {
            int dx = Math.abs(pos.x - playerChunkX);
            int dz = Math.abs(pos.z - playerChunkZ);
            return dx > viewDist + 3 || dz > viewDist + 3;
        });

        suspiciousBlocks.entrySet().removeIf(entry -> {
            BlockPos blockPos = entry.getKey();
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(blockPos));
            return distance > viewDist * 16 + 80;
        });
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        // Render chunk highlights
        if (!flaggedChunks.isEmpty()) {
            Color highlight = new Color(chunkColor.get());
            int rendered = 0;
            for (ChunkPos pos : flaggedChunks) {
                if (rendered++ > 50) break;
                renderChunkHighlight(event, pos, highlight);
            }
        }

        // Render individual suspicious blocks
        if (highlightBlocks.get()) {
            renderSuspiciousBlocks(event);
        }
    }

    private void renderChunkHighlight(Render3DEvent event, ChunkPos pos, Color color) {
        int startX = pos.getStartX();
        int startZ = pos.getStartZ();
        int endX = pos.getEndX();
        int endZ = pos.getEndZ();

        double y = renderY.get();
        double h = 0.3;

        Box box = new Box(startX, y, startZ, endX + 1, y + h, endZ + 1);
        event.renderer.box(box, color, color, renderMode.get(), 0);
    }

    private void renderSuspiciousBlocks(Render3DEvent event) {
        int rendered = 0;

        for (Map.Entry<BlockPos, SuspiciousBlock> entry : suspiciousBlocks.entrySet()) {
            if (rendered >= maxBlocksToRender.get()) break;

            BlockPos pos = entry.getKey();
            SuspiciousBlock suspiciousBlock = entry.getValue();

            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > mc.options.getViewDistance().getValue() * 16) continue;

            Color blockColor = getColorForBlockType(suspiciousBlock.type);
            if (blockColor != null) {
                Box box = new Box(pos);
                event.renderer.box(box, blockColor, blockColor, ShapeMode.Lines, 0);
                rendered++;
            }
        }
    }

    private Color getColorForBlockType(SuspiciousBlockType type) {
        return switch (type) {
            case COBBLED_DEEPSLATE -> new Color(cobbledDeepslateColor.get());
            case ROTATED_DEEPSLATE -> new Color(rotatedDeepslateColor.get());
            case END_STONE -> new Color(endStoneColor.get());
            default -> null;
        };
    }

    private boolean isCobbledDeepslate(BlockState state) {
        return state.getBlock() == Blocks.COBBLED_DEEPSLATE;
    }

    private boolean isRotatedDeepslate(BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.DEEPSLATE) return false;
        if (!state.contains(Properties.AXIS)) return false;
        Direction.Axis axis = state.get(Properties.AXIS);
        return axis != Direction.Axis.Y;
    }

    private boolean isEndStone(BlockState state) {
        return state.getBlock() == Blocks.END_STONE;
    }

    @Override
    public String getInfoString() {
        if (highlightBlocks.get()) {
            return String.format("C:%d B:%d", flaggedChunks.size(), suspiciousBlocks.size());
        }
        return String.valueOf(flaggedChunks.size());
    }

    private static class ChunkAnalysis {
        int cobbledDeepslateCount = 0;
        int rotatedDeepslateCount = 0;
        int endStoneCount = 0;
        int trialChamberCount = 0;
    }

    private static class SuspiciousBlock {
        final SuspiciousBlockType type;
        final long detectedTime;

        SuspiciousBlock(SuspiciousBlockType type, long detectedTime) {
            this.type = type;
            this.detectedTime = detectedTime;
        }
    }

    private enum SuspiciousBlockType {
        COBBLED_DEEPSLATE,
        ROTATED_DEEPSLATE,
        END_STONE
    }
}
