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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerFinder extends Module {
    public enum SpawnerType {
        Any,
        Zombie,
        Skeleton,
        Spider,
        Creeper,
        Enderman,
        Silverfish,
        Pig,
        Sheep,
        Cow,
        Chicken,
        Villager,
        Blaze,
        MagmaCube,
        WitherSkeleton,
        Other
    }

    public enum NotificationMode {
        Chat,
        Sound,
        Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    // Detection filters
    private final Setting<Boolean> detectZombie = sgFilters.add(new BoolSetting.Builder()
        .name("detect-zombie")
        .description("Detect zombie spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectSkeleton = sgFilters.add(new BoolSetting.Builder()
        .name("detect-skeleton")
        .description("Detect skeleton spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectSpider = sgFilters.add(new BoolSetting.Builder()
        .name("detect-spider")
        .description("Detect spider spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectCreeper = sgFilters.add(new BoolSetting.Builder()
        .name("detect-creeper")
        .description("Detect creeper spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectEnderman = sgFilters.add(new BoolSetting.Builder()
        .name("detect-enderman")
        .description("Detect enderman spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectBlaze = sgFilters.add(new BoolSetting.Builder()
        .name("detect-blaze")
        .description("Detect blaze spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectMagmaCube = sgFilters.add(new BoolSetting.Builder()
        .name("detect-magma-cube")
        .description("Detect magma cube spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectWitherSkeleton = sgFilters.add(new BoolSetting.Builder()
        .name("detect-wither-skeleton")
        .description("Detect wither skeleton spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectOther = sgFilters.add(new BoolSetting.Builder()
        .name("detect-other")
        .description("Detect other spawners (pig, sheep, cow, etc.)")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectSilverfish = sgFilters.add(new BoolSetting.Builder()
        .name("detect-silverfish")
        .description("Detect silverfish spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectVillager = sgFilters.add(new BoolSetting.Builder()
        .name("detect-villager")
        .description("Detect villager spawners")
        .defaultValue(true)
        .build()
    );

    // Render settings
    private final Setting<Boolean> showEsp = sgRender.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Show spawner ESP")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> zombieColor = sgRender.add(new ColorSetting.Builder()
        .name("zombie-color")
        .description("Color for zombie spawners")
        .defaultValue(new SettingColor(50, 150, 50, 50))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> skeletonColor = sgRender.add(new ColorSetting.Builder()
        .name("skeleton-color")
        .description("Color for skeleton spawners")
        .defaultValue(new SettingColor(200, 200, 200, 50))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> spiderColor = sgRender.add(new ColorSetting.Builder()
        .name("spider-color")
        .description("Color for spider spawners")
        .defaultValue(new SettingColor(100, 50, 150, 50))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> creeperColor = sgRender.add(new ColorSetting.Builder()
        .name("creeper-color")
        .description("Color for creeper spawners")
        .defaultValue(new SettingColor(0, 200, 0, 50))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> blazeColor = sgRender.add(new ColorSetting.Builder()
        .name("blaze-color")
        .description("Color for blaze spawners")
        .defaultValue(new SettingColor(255, 150, 0, 50))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> otherColor = sgRender.add(new ColorSetting.Builder()
        .name("other-color")
        .description("Color for other spawner types")
        .defaultValue(new SettingColor(255, 255, 0, 50))
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

    // Notification settings
    private final Setting<Boolean> notifications = sgNotifications.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications when spawners are found")
        .defaultValue(true)
        .build()
    );

    private final Setting<NotificationMode> notificationMode = sgNotifications.add(new EnumSetting.Builder<NotificationMode>()
        .name("notification-mode")
        .description("How to notify when spawners are found")
        .defaultValue(NotificationMode.Both)
        .visible(notifications::get)
        .build()
    );

    private final Setting<Boolean> playSound = sgNotifications.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play sound when spawner is found")
        .defaultValue(true)
        .visible(() -> notificationMode.get() == NotificationMode.Sound || notificationMode.get() == NotificationMode.Both)
        .build()
    );

    private final Setting<Boolean> showDistance = sgNotifications.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Show distance to spawner")
        .defaultValue(true)
        .visible(notifications::get)
        .build()
    );

    // Data storage
    private final Set<BlockPos> foundSpawners = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, SpawnerType> spawnerTypes = new ConcurrentHashMap<>();
    private int totalFound = 0;

    public SpawnerFinder() {
        super(AddonTemplate.CATEGORY, "spawner-finder", "Finds and highlights spawners by type for Donut SMP base finding.");
    }

    @Override
    public void onActivate() {
        foundSpawners.clear();
        spawnerTypes.clear();
        totalFound = 0;

        // Scan loaded chunks
        for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) {
                scanChunk(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        foundSpawners.clear();
        spawnerTypes.clear();
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
                
                if (foundSpawners.contains(pos)) continue;

                SpawnerType type = getSpawnerType(pos);
                
                if (shouldDetect(type)) {
                    foundSpawners.add(pos);
                    spawnerTypes.put(pos, type);
                    totalFound++;
                    notifySpawner(pos, type);
                }
            }
        }
    }

    private SpawnerType getSpawnerType(BlockPos pos) {
        // For now, just detect all spawners - type detection would need server data
        // Could be enhanced with client-side scanning
        return SpawnerType.Other;
    }

    private boolean shouldDetect(SpawnerType type) {
        return switch (type) {
            case Zombie -> detectZombie.get();
            case Skeleton -> detectSkeleton.get();
            case Spider -> detectSpider.get();
            case Creeper -> detectCreeper.get();
            case Enderman -> detectEnderman.get();
            case Blaze -> detectBlaze.get();
            case MagmaCube -> detectMagmaCube.get();
            case WitherSkeleton -> detectWitherSkeleton.get();
            case Silverfish -> detectSilverfish.get();
            case Villager -> detectVillager.get();
            case Other -> detectOther.get();
            default -> true;
        };
    }

    private void notifySpawner(BlockPos pos, SpawnerType type) {
        if (!notifications.get()) return;

        String typeName = type.name().toLowerCase();
        String message = String.format("§5[§dSpawner§5] §b%s spawner §5at §b%d, %d, %d", 
            typeName, pos.getX(), pos.getY(), pos.getZ());

        if (showDistance.get() && mc.player != null) {
            double dist = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            message += String.format(" §5(§b%.1fm§5)", dist);
        }

        info(message);

        if (playSound.get()) {
            mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!showEsp.get()) return;

        for (Map.Entry<BlockPos, SpawnerType> entry : spawnerTypes.entrySet()) {
            BlockPos pos = entry.getKey();
            SpawnerType type = entry.getValue();
            
            Color color = getColorForType(type);
            event.renderer.box(pos, color, color, shapeMode.get(), 0);
        }
    }

    private Color getColorForType(SpawnerType type) {
        return switch (type) {
            case Zombie -> new Color(zombieColor.get());
            case Skeleton -> new Color(skeletonColor.get());
            case Spider -> new Color(spiderColor.get());
            case Creeper -> new Color(creeperColor.get());
            case Blaze -> new Color(blazeColor.get());
            default -> new Color(otherColor.get());
        };
    }

    @Override
    public String getInfoString() {
        return String.valueOf(totalFound);
    }
}
