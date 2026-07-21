package com.example.addon.mixin;

import com.example.addon.modules.FastMineV1;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class FastMineMixin {
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetBlockBreakingSpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        FastMineV1 module = FastMineV1.INSTANCE;
        if (module == null || !module.isActive()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.isCreative() || player.isSpectator()) return;
        if (block == null || block.isAir() || block.getBlock() == Blocks.BEDROCK) return;

        float originalSpeed = cir.getReturnValue();
        if (originalSpeed <= 0.0f) return;

        float multiplier = Math.max(1.0f, module.speed.get().floatValue());
        if (module.sprintBoost.get()) {
            boolean sprinting = player.isSprinting();
            boolean moving = player.getVelocity().horizontalLengthSquared() > 0.0001f;

            if (sprinting || moving) {
                multiplier *= module.sprintMultiplier.get().floatValue();
            }
        }

        cir.setReturnValue(originalSpeed * multiplier);
    }
}
