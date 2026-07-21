package com.example.addon.mixin;

import com.example.addon.modules.FastMineV1;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class FastMineMixin {
    
    /**
     * Inject at the start of getBlockBreakingSpeed to capture the target block
     * This allows us to track what block the player is trying to mine
     */
    @Inject(method = "getBlockBreakingSpeed", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetBlockBreakingSpeedHead(BlockState block, CallbackInfoReturnable<Float> cir) {
        FastMineV1 module = FastMineV1.INSTANCE;
        if (module == null || !module.isActive()) return;
        
        // In Silent mode, we want to be more subtle
        if (module.mode.get() == FastMineV1.Mode.Silent) {
            // Let the original speed pass through most of the time
            // Only apply slight boosts to avoid detection
            PlayerEntity player = (PlayerEntity) (Object) this;
            if (player.isCreative() || player.isSpectator()) return;
            if (block == null || block.isAir() || block.getBlock() == Blocks.BEDROCK) return;
            
            // Very conservative speed boost for silent mode
            float originalSpeed = cir.getReturnValue();
            if (originalSpeed <= 0.0f) return;
            
            // Only apply minimal multiplier in silent mode
            float silentMultiplier = 1.1f; // 10% boost - very subtle
            cir.setReturnValue(originalSpeed * silentMultiplier);
        }
    }

    /**
     * Main speed modification at RETURN - applies the configured multiplier
     */
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetBlockBreakingSpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        FastMineV1 module = FastMineV1.INSTANCE;
        if (module == null || !module.isActive()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.isCreative() || player.isSpectator()) return;
        if (block == null || block.isAir() || block.getBlock() == Blocks.BEDROCK) return;

        float originalSpeed = cir.getReturnValue();
        if (originalSpeed <= 0.0f) return;

        // Skip if we already modified it in HEAD (silent mode)
        if (module.mode.get() == FastMineV1.Mode.Silent) {
            // Silent mode already handled, apply additional slight boost if needed
            return;
        }

        // Get effective speed from module (adaptive or configured)
        float multiplier = module.getEffectiveSpeed();
        
        // Cap the multiplier to avoid extremely fast mining
        // Most anti-cheats flag anything over 5x speed
        multiplier = Math.min(multiplier, 5.0f);

        // Add some variance to make it less detectable
        float variance = 1.0f + (float) (Math.random() * 0.1 - 0.05); // ±5% variance
        multiplier *= variance;

        float newSpeed = originalSpeed * multiplier;
        
        // Clamp to prevent overflow
        newSpeed = Math.min(newSpeed, 100.0f);
        
        cir.setReturnValue(newSpeed);
    }

    /**
     * Remove the break delay - called when player starts breaking a block
     * This is more subtle than modifying the actual breaking speed
     */
    @Inject(method = "resetLastAttackedTicks", at = @At("HEAD"), cancellable = true, remap = false)
    private void onResetLastAttackedTicks(CallbackInfoReturnable<Integer> cir) {
        FastMineV1 module = FastMineV1.INSTANCE;
        if (module == null || !module.isActive()) return;
        
        if (module.shouldIgnoreDelay()) {
            // Return 0 to remove the attack cooldown delay
            cir.setReturnValue(0);
            cir.cancel();
        }
    }
    
    /**
     * Track block break attempts for the module
     * This helps with adaptive speed management
     */
    @Inject(method = "attack", at = @At("HEAD"), remap = false)
    private void onAttack(CallbackInfo ci) {
        FastMineV1 module = FastMineV1.INSTANCE;
        if (module == null || !module.isActive()) return;
        
        // Could be used to track mining attempts
        // The actual block position tracking would need to be done elsewhere
    }
}
