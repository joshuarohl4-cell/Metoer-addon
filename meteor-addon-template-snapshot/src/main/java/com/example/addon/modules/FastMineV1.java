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
        .defaultValue(FastMinePreset.Haste10)
        .onChanged(preset -> applyPreset(preset))
        .build()
    );

    // Custom speed if preset is Custom
    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Mining speed (1.0 = normal, 10.0 = Haste 10)")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .visible(() -> preset.get() == FastMinePreset.Custom)
        .build()
    );

    // Internal state
    private float effectiveSpeed = 5.0f;

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
            case Haste10:
                // Haste 10 level - super fast
                effectiveSpeed = 5.0f;
                break;
            case Instant:
                // Near instant mine
                effectiveSpeed = 8.0f;
                break;
            case GodMode:
                // Max speed
                effectiveSpeed = 10.0f;
                break;
            case Aggressive:
                effectiveSpeed = 4.0f;
                break;
            case Normal:
                effectiveSpeed = 3.0f;
                break;
            case AntiCheat:
                // Bypasses most anti-cheats
                effectiveSpeed = 3.5f;
                break;
            case Custom:
                effectiveSpeed = speed.get().floatValue();
                break;
        }
    }

    public float getEffectiveSpeed() {
        // Apply slight random variance to avoid detection
        if (preset.get() != FastMinePreset.Custom) {
            float variance = (float) (Math.random() * 0.08 - 0.04); // ±4%
            return effectiveSpeed * (1.0f + variance);
        }
        return effectiveSpeed;
    }

    public enum FastMinePreset {
        Haste10("Haste 10", "Super fast - like Haste 10 potion"),
        Instant("Instant", "Near instant block breaking"),
        GodMode("God Mode", "Maximum possible speed"),
        Aggressive("Aggressive", "Very fast 4x speed"),
        Normal("Normal", "Fast 3x speed"),
        AntiCheat("Anti-Cheat", "Balanced speed for servers with anti-cheat"),
        Custom("Custom", "Set your own speed (1-10)");

        private final String name;
        private final String description;

        FastMinePreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
