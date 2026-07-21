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

    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void onGetBlockBreakingSpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        FastMineV1 module = FastMineV1.INSTANCE;
        if (module == null || !module.isActive()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        
        // Don't affect creative or spectator mode
        if (player.isCreative() || player.isSpectator()) return;
        
        // Don't affect air or bedrock
        if (block == null || block.isAir()) return;
        if (block.getBlock() == Blocks.BEDROCK) return;

        float baseSpeed = cir.getReturnValue();
        if (baseSpeed <= 0.0f) return;

        // Get the speed multiplier from module
        float speedMultiplier = module.getEffectiveSpeed();
        
        // Apply reasonable cap
        speedMultiplier = Math.min(speedMultiplier, 10.0f);
        
        // Calculate new speed
        float newSpeed = baseSpeed * speedMultiplier;
        
        // Clamp to prevent overflow
        newSpeed = Math.min(newSpeed, 100.0f);
        
        cir.setReturnValue(newSpeed);
    }
}
