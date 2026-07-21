package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import meteordevelopment.orbit.EventHandler;

import java.util.HashMap;
import java.util.Map;

public class FastMineV1 extends Module {
    public static FastMineV1 INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgProfiles = settings.createGroup("Anti-Cheat Profiles");

    // General settings
    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Mining acceleration mode")
        .defaultValue(Mode.Silent)
        .build()
    );

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Mining speed multiplier (1.0 = vanilla)")
        .defaultValue(1.5)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .build()
    );

    public final Setting<Boolean> ignoreDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("no-break-delay")
        .description("Remove the small delay before breaking blocks")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> spoofProgress = sgGeneral.add(new BoolSetting.Builder()
        .name("spoof-progress")
        .description("Send silent block break packets to fake progress (use with caution)")
        .defaultValue(false)
        .build()
    );

    // Timing settings
    public final Setting<Integer> minDelay = sgTiming.add(new IntSetting.Builder()
        .name("min-packet-delay")
        .description("Minimum delay between packets (ms)")
        .defaultValue(50)
        .min(0)
        .max(200)
        .sliderRange(0, 200)
        .build()
    );

    public final Setting<Integer> maxDelay = sgTiming.add(new IntSetting.Builder()
        .name("max-packet-delay")
        .description("Maximum delay between packets (ms)")
        .defaultValue(100)
        .min(0)
        .max(500)
        .sliderRange(0, 500)
        .build()
    );

    public final Setting<Integer> variance = sgTiming.add(new IntSetting.Builder()
        .name("random-variance")
        .description("Random variance in timing to avoid patterns (ms)")
        .defaultValue(15)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    // Anti-cheat profiles
    public final Setting<AntiCheatProfile> antiCheatProfile = sgProfiles.add(new EnumSetting.Builder<AntiCheatProfile>()
        .name("anti-cheat-profile")
        .description("Pre-configured settings for specific anti-cheats")
        .defaultValue(AntiCheatProfile.Custom)
        .onChanged(profile -> applyProfile(profile))
        .build()
    );

    public final Setting<Boolean> adaptiveSpeed = sgProfiles.add(new BoolSetting.Builder()
        .name("adaptive-speed")
        .description("Automatically adjust speed based on server behavior")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> maxSpeed = sgProfiles.add(new DoubleSetting.Builder()
        .name("max-adaptive-speed")
        .description("Maximum speed when adapting")
        .defaultValue(4.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .build()
    );

    // Internal state
    private final Map<BlockPos, BreakData> breakingBlocks = new HashMap<>();
    private BlockPos currentTarget = null;
    private int ticksSinceLastPacket = 0;
    private int currentDelay;
    private float lastReportedProgress = 0f;
    private long lastPacketTime = 0;
    private int consecutivePackets = 0;
    private int serverViolations = 0;
    private double currentEffectiveSpeed = 1.0;

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Anti-cheat friendly mining acceleration.");
        INSTANCE = this;
        currentDelay = minDelay.get();
    }

    @Override
    public void onActivate() {
        if (INSTANCE != this) INSTANCE = this;
        breakingBlocks.clear();
        currentTarget = null;
        lastPacketTime = System.currentTimeMillis();
        currentEffectiveSpeed = speed.get();
        
        if (mc.player != null) {
            applyProfile(antiCheatProfile.get());
        }
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
        breakingBlocks.clear();
        currentTarget = null;
        
        // Send abort packet for any block we were breaking
        if (mc.player != null && currentTarget != null) {
            sendAbortPacket(currentTarget);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Adaptive speed management
        if (adaptiveSpeed.get()) {
            manageAdaptiveSpeed();
        }

        // Clean up old block data
        cleanupBlockData();

        // Check if we need to send progress packets
        if (spoofProgress.get() && currentTarget != null) {
            handleProgressSpoofing();
        }

        ticksSinceLastPacket++;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;

        // Track server responses for adaptive speed
        if (event.packet instanceof BlockUpdateS2CPacket) {
            BlockUpdateS2CPacket packet = (BlockUpdateS2CPacket) event.packet;
            BlockPos pos = packet.getPos();
            
            // Server is sending block updates - might be rejecting our breaks
            if (currentTarget != null && pos.equals(currentTarget)) {
                BlockState state = packet.getState();
                if (state.isAir() || state.getBlock() != Blocks.AIR) {
                    // Block wasn't broken or was restored - reduce speed
                    serverViolations++;
                    if (adaptiveSpeed.get()) {
                        reduceSpeed();
                    }
                }
            }
        }
    }

    private void manageAdaptiveSpeed() {
        // Slowly increase speed if we're not getting violations
        if (serverViolations == 0 && currentEffectiveSpeed < maxSpeed.get()) {
            if (Math.random() < 0.01) { // 1% chance per tick
                currentEffectiveSpeed = Math.min(currentEffectiveSpeed + 0.1, maxSpeed.get());
            }
        }
        
        // Decay violations over time
        if (serverViolations > 0 && Math.random() < 0.1) {
            serverViolations--;
        }
    }

    private void reduceSpeed() {
        currentEffectiveSpeed = Math.max(1.0, currentEffectiveSpeed - 0.5);
        currentDelay = Math.min(currentDelay + 10, maxDelay.get());
    }

    private void cleanupBlockData() {
        long currentTime = System.currentTimeMillis();
        breakingBlocks.entrySet().removeIf(entry -> {
            BreakData data = entry.getValue();
            // Remove entries older than 30 seconds
            return currentTime - data.startTime > 30000;
        });
    }

    private void handleProgressSpoofing() {
        if (mc.world == null) return;

        BlockPos target = currentTarget;
        if (target == null) return;

        BlockState state = mc.world.getBlockState(target);
        if (state.isAir()) {
            currentTarget = null;
            return;
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceLastPacket = currentTime - lastPacketTime;

        // Calculate delay with variance
        int effectiveDelay = currentDelay + (int) ((Math.random() * 2 - 1) * variance.get());

        if (timeSinceLastPacket >= effectiveDelay) {
            // Calculate progress to report
            float blockHardness = getAdjustedHardness(state);
            float progressPerTick = getProgressPerTick(blockHardness);
            
            lastReportedProgress = Math.min(lastReportedProgress + progressPerTick, 1.0f);

            // Only send packet if we have meaningful progress
            if (lastReportedProgress > 0.01f) {
                sendProgressPacket(target, (int) (lastReportedProgress * 100));
                lastPacketTime = currentTime;
                ticksSinceLastPacket = 0;
                consecutivePackets++;
            }

            // Reset if block is broken
            if (lastReportedProgress >= 1.0f) {
                lastReportedProgress = 0f;
                currentTarget = null;
            }
        }
    }

    private float getAdjustedHardness(BlockState state) {
        float hardness = state.getHardness(mc.world, currentTarget);
        if (hardness < 0) hardness = 0;
        return hardness;
    }

    private float getProgressPerTick(float hardness) {
        if (hardness <= 0) return 1f;
        
        float baseProgress = 1f / (hardness * 20 * (float) currentEffectiveSpeed);
        return Math.min(baseProgress, 0.2f); // Cap to avoid too fast breaks
    }

    private void sendProgressPacket(BlockPos pos, int progress) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        // Use EntityInteractionC2SPacket for silent block progress
        // This is less detectable than direct PlayerActionC2SPackets
        try {
            // Alternative: use CreativeInventoryPacket or custom interaction
            // For now, we'll rely on the mixin for actual speed modification
        } catch (Exception e) {
            // Silently fail - don't alert anti-cheat
        }
    }

    private void sendAbortPacket(BlockPos pos) {
        if (mc.player == null) return;

        try {
            PlayerActionC2SPacket abortPacket = new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                pos,
                Direction.DOWN
            );
            mc.getNetworkHandler().sendPacket(abortPacket);
        } catch (Exception e) {
            // Silently fail
        }
    }

    private void applyProfile(AntiCheatProfile profile) {
        switch (profile) {
            case Vulcan:
                speed.set(1.3);
                ignoreDelay.set(true);
                spoofProgress.set(false);
                minDelay.set(80);
                maxDelay.set(150);
                variance.set(30);
                adaptiveSpeed.set(true);
                break;
            case Grim:
                speed.set(1.5);
                ignoreDelay.set(true);
                spoofProgress.set(false);
                minDelay.set(60);
                maxDelay.set(120);
                variance.set(25);
                adaptiveSpeed.set(true);
                break;
            case Spartan:
                speed.set(2.0);
                ignoreDelay.set(true);
                spoofProgress.set(false);
                minDelay.set(40);
                maxDelay.set(80);
                variance.set(15);
                adaptiveSpeed.set(false);
                break;
            case Custom:
                // Keep user settings
                break;
            case Off:
                speed.set(1.0);
                ignoreDelay.set(false);
                spoofProgress.set(false);
                adaptiveSpeed.set(false);
                break;
        }
        currentEffectiveSpeed = speed.get();
        currentDelay = minDelay.get();
    }

    public float getEffectiveSpeed() {
        return (float) currentEffectiveSpeed;
    }

    public boolean shouldIgnoreDelay() {
        return ignoreDelay.get() && mode.get() != Mode.Vanilla;
    }

    public boolean isSpoofingEnabled() {
        return spoofProgress.get() && mode.get() == Mode.Silent;
    }

    public int getCurrentDelay() {
        return currentDelay + (int) ((Math.random() * 2 - 1) * variance.get());
    }

    public void setCurrentTarget(BlockPos pos) {
        this.currentTarget = pos;
        this.lastReportedProgress = 0f;
        
        // Add to tracking
        breakingBlocks.put(pos, new BreakData(System.currentTimeMillis()));
    }

    private static class BreakData {
        long startTime;
        float lastProgress;

        BreakData(long startTime) {
            this.startTime = startTime;
            this.lastProgress = 0f;
        }
    }

    public enum Mode {
        Vanilla("Vanilla", "Standard speed multiplier"),
        Silent("Silent", "Subtle speed boost, less detectable"),
        Aggressive("Aggressive", "Faster mining, more noticeable");
        
        private final String name;
        private final String description;

        Mode(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public enum AntiCheatProfile {
        Custom("Custom", "Use your custom settings"),
        Vulcan("Vulcan", "Optimized for Vulcan anti-cheat"),
        Grim("Grim", "Optimized for Grim anti-cheat"),
        Spartan("Spartan", "Optimized for Spartan anti-cheat"),
        Off("Off", "Minimal modifications, highest bypass");

        private final String name;
        private final String description;

        AntiCheatProfile(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
