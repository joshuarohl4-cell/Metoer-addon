package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class AutoMine extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> stopOnBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-on-break")
        .description("Stop auto mining when a block is broken.")
        .defaultValue(true)
        .build()
    );

    private boolean isMining = false;

    public AutoMine() {
        super(AddonTemplate.CATEGORY, "auto-mine", "Automatically holds left click to mine blocks.");
    }

    @Override
    public void onDeactivate() {
        mc.options.attackKey.setPressed(false);
        isMining = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        // Get the block the player is looking at
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            mc.options.attackKey.setPressed(false);
            isMining = false;
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();

        // Check if it's a valid block to mine
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK || state.getHardness(mc.world, pos) < 0) {
            mc.options.attackKey.setPressed(false);
            isMining = false;
            return;
        }

        // Hold the attack key to mine
        mc.options.attackKey.setPressed(true);
        isMining = true;
    }
}
