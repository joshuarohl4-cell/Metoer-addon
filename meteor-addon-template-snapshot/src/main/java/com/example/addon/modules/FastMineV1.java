package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class FastMineV1 extends Module {
    public static FastMineV1 INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Speed presets
    public final Setting<SpeedPreset> speedPreset = sgGeneral.add(new EnumSetting.Builder<SpeedPreset>()
        .name("speed")
        .description("Mining speed preset")
        .defaultValue(SpeedPreset.Haste10)
        .onChanged(preset -> applySpeedPreset(preset))
        .build()
    );

    // Custom speed slider
    public final Setting<Double> customSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("custom-speed")
        .description("Custom mining speed (1.0 = normal)")
        .defaultValue(5.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .visible(() -> speedPreset.get() == SpeedPreset.Custom)
        .build()
    );

    // Server specific presets
    public final Setting<ServerPreset> serverPreset = sgGeneral.add(new EnumSetting.Builder<ServerPreset>()
        .name("server")
        .description("Auto-configure for specific server type")
        .defaultValue(ServerPreset.Any)
        .onChanged(preset -> applyServerPreset(preset))
        .build()
    );

    // Internal state
    private float effectiveSpeed = 10.0f;

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Mine blocks faster with anti-cheat bypass.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (INSTANCE != this) INSTANCE = this;
        applySpeedPreset(speedPreset.get());
        applyServerPreset(serverPreset.get());
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
    }

    private void applySpeedPreset(SpeedPreset preset) {
        switch (preset) {
            case Haste2:
                effectiveSpeed = 2.0f;
                break;
            case Haste5:
                effectiveSpeed = 5.0f;
                break;
            case Haste10:
                effectiveSpeed = 10.0f;
                break;
            case Haste15:
                effectiveSpeed = 15.0f;
                break;
            case Instant:
                effectiveSpeed = 20.0f;
                break;
            case Custom:
                effectiveSpeed = customSpeed.get().floatValue();
                break;
        }
    }

    private void applyServerPreset(ServerPreset preset) {
        switch (preset) {
            case DonutSMP:
                speedPreset.set(SpeedPreset.Haste10);
                applySpeedPreset(SpeedPreset.Haste10);
                break;
            case EuropeSMP:
                speedPreset.set(SpeedPreset.Haste10);
                applySpeedPreset(SpeedPreset.Haste10);
                break;
            case Hypixel:
                speedPreset.set(SpeedPreset.Haste2);
                applySpeedPreset(SpeedPreset.Haste2);
                break;
            case Vanilla:
                speedPreset.set(SpeedPreset.Instant);
                applySpeedPreset(SpeedPreset.Instant);
                break;
            case Any:
                // Keep current settings
                break;
        }
    }

    public float getEffectiveSpeed() {
        // Apply random variance to avoid detection
        float variance = (float) (Math.random() * 0.04 - 0.02); // ±2%
        return effectiveSpeed * (1.0f + variance);
    }

    public enum SpeedPreset {
        Haste2("Haste 2", "Light 2x speed boost"),
        Haste5("Haste 5", "Medium 5x speed boost"),
        Haste10("Haste 10", "Strong 10x speed boost"),
        Haste15("Haste 15", "Very strong 15x speed boost"),
        Instant("Instant", "Near instant 20x speed"),
        Custom("Custom", "Set your own speed");

        private final String name;
        private final String description;

        SpeedPreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public enum ServerPreset {
        Any("Any Server", "Use default settings"),
        DonutSMP("Donut SMP", "Optimized for Donut SMP"),
        EuropeSMP("Europe SMP", "Optimized for Europe SMP"),
        Hypixel("Hypixel", "Optimized for Hypixel"),
        Vanilla("Vanilla", "Maximum speed for vanilla/offline servers");

        private final String name;
        private final String description;

        ServerPreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
