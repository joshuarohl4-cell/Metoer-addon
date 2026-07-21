package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class SpawnerChunks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the spawner chunks.")
        .defaultValue(new SettingColor(255, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the spawner chunks.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> rescanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-ticks")
        .description("How often (in ticks) to rescan nearby chunks for spawners.")
        .defaultValue(20)
        .min(5)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> scanRange = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("How many chunks to scan around you.")
        .defaultValue(5)
        .min(1)
        .max(15)
        .sliderMax(15)
        .build()
    );

    private final Set<ChunkPos> spawnerChunks = new HashSet<>();
    private int tickCounter = 0;

    public SpawnerChunks() {
        super(AddonTemplate.CATEGORY, "spawner-chunks", "Highlights chunks that contain spawners.");
    }

    @Override
    public void onActivate() {
        spawnerChunks.clear();
        rescan();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= rescanInterval.get()) {
            tickCounter = 0;
            rescan();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (ChunkPos chunkPos : spawnerChunks) {
            event.renderer.box(
                chunkPos.x, 0, chunkPos.z,
                chunkPos.x + 1, 256, chunkPos.z + 1,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }

    private void rescan() {
        if (mc.world == null || mc.player == null) return;

        spawnerChunks.clear();

        int range = scanRange.get();
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                if (hasSpawner(chunkX, chunkZ)) {
                    spawnerChunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
    }

    private boolean hasSpawner(int chunkX, int chunkZ) {
        World world = mc.world;
        if (world == null) return false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y <= 320; y++) {
                    BlockPos pos = new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z);
                    if (world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
