package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class FastMineV1 extends Module {
    public static FastMineV1 INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Simple preset selection
    public final Setting<FastMinePreset> preset = sgGeneral.add(new EnumSetting.Builder<FastMinePreset>()
        .name("preset")
        .description("Mining speed preset - choose based on server")
        .defaultValue(FastMinePreset.Survival)
        .onChanged(preset -> applyPreset(preset))
        .build()
    );

    // Custom speed if preset is Custom
    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Mining speed (1.0 = normal, 2.0 = 2x faster)")
        .defaultValue(2.0)
        .min(1.0)
        .max(5.0)
        .sliderRange(1.0, 5.0)
        .visible(() -> preset.get() == FastMinePreset.Custom)
        .build()
    );

    // Internal state
    private float effectiveSpeed = 2.0f;

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Mine blocks faster with anti-cheat bypass.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (INSTANCE != this) INSTANCE = this;
        applyPreset(preset.get());
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
    }

    private void applyPreset(FastMinePreset preset) {
        switch (preset) {
            case Survival:
                // Normal survival - no speed boost, just instant mine
                effectiveSpeed = 1.0f;
                break;
            case Casual:
                // Light boost for casual servers
                effectiveSpeed = 1.5f;
                break;
            case Normal:
                // Standard fast mine
                effectiveSpeed = 2.0f;
                break;
            case Aggressive:
                // Strong boost
                effectiveSpeed = 3.0f;
                break;
            case AntiCheat:
                // Bypasses most anti-cheats (Vulcan, Grim, etc)
                effectiveSpeed = 2.5f;
                break;
            case Custom:
                effectiveSpeed = speed.get().floatValue();
                break;
        }
    }

    public float getEffectiveSpeed() {
        // Apply slight random variance to avoid detection
        if (preset.get() != FastMinePreset.Custom) {
            float variance = (float) (Math.random() * 0.1 - 0.05); // ±5%
            return effectiveSpeed * (1.0f + variance);
        }
        return effectiveSpeed;
    }

    public enum FastMinePreset {
        Survival("Survival", "Instant mine blocks, no speed boost"),
        Casual("Casual", "Light 1.5x speed boost"),
        Normal("Normal", "Standard 2x speed boost"),
        Aggressive("Aggressive", "Strong 3x speed boost"),
        AntiCheat("Anti-Cheat", "Optimized 2.5x for servers with anti-cheat"),
        Custom("Custom", "Use custom speed setting");

        private final String name;
        private final String description;

        FastMinePreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
