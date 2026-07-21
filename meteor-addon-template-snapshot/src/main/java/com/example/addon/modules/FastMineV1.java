package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class FastMineV1 extends Module {
    public static FastMineV1 INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Anti-Cheat Bypass");

    // Mining speed presets (SAFER VALUES)
    public final Setting<SpeedPreset> speedPreset = sgGeneral.add(new EnumSetting.Builder<SpeedPreset>()
        .name("speed")
        .description("Mining speed - lower = safer from detection")
        .defaultValue(SpeedPreset.Haste3)
        .build()
    );

    // Custom speed slider
    public final Setting<Double> customSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("custom-speed")
        .description("Custom mining speed (1.0 = normal)")
        .defaultValue(2.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .visible(() -> speedPreset.get() == SpeedPreset.Custom)
        .build()
    );

    // Server specific presets (SAFE VALUES)
    public final Setting<ServerPreset> serverPreset = sgGeneral.add(new EnumSetting.Builder<ServerPreset>()
        .name("server")
        .description("Auto-configure for specific server")
        .defaultValue(ServerPreset.DonutSMP)
        .onChanged(preset -> applyServerPreset(preset))
        .build()
    );

    // Internal state
    private float effectiveSpeed = 3.0f;

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Mine blocks faster with anti-cheat bypass techniques.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (INSTANCE != this) INSTANCE = this;
        applySpeedPreset();
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
    }

    private void applySpeedPreset() {
        switch (speedPreset.get()) {
            case Haste1:
                effectiveSpeed = 1.5f;
                break;
            case Haste2:
                effectiveSpeed = 2.0f;
                break;
            case Haste3:
                effectiveSpeed = 3.0f;
                break;
            case Haste5:
                effectiveSpeed = 5.0f;
                break;
            case Haste10:
                effectiveSpeed = 8.0f;
                break;
            case Custom:
                effectiveSpeed = customSpeed.get().floatValue();
                break;
        }
    }

    private void applyServerPreset(ServerPreset preset) {
        switch (preset) {
            case DonutSMP:
                speedPreset.set(SpeedPreset.Haste2);
                applySpeedPreset();
                break;
            case EuropeSMP:
                speedPreset.set(SpeedPreset.Haste2);
                applySpeedPreset();
                break;
            case Hypixel:
                speedPreset.set(SpeedPreset.Haste1);
                applySpeedPreset();
                break;
            case Vanilla:
                speedPreset.set(SpeedPreset.Haste5);
                applySpeedPreset();
                break;
            case Any:
                break;
        }
    }

    public float getEffectiveSpeed() {
        return effectiveSpeed;
    }

    public enum SpeedPreset {
        Haste1("Haste 1", "Subtle 1.5x boost (safest)"),
        Haste2("Haste 2", "Moderate 2x boost (recommended)"),
        Haste3("Haste 3", "Fast 3x boost"),
        Haste5("Haste 5", "Very fast 5x boost"),
        Haste10("Haste 10", "Extreme 8x boost (risky)"),
        Custom("Custom", "Set your own speed");

        private final String name;
        private final String description;

        SpeedPreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public enum ServerPreset {
        Any("Any Server", "Use manual settings"),
        DonutSMP("Donut SMP", "Safe bypass settings"),
        EuropeSMP("Europe SMP", "Safe bypass settings"),
        Hypixel("Hypixel", "Very safe settings"),
        Vanilla("Vanilla", "Maximum speed, no bypass");

        private final String name;
        private final String description;

        ServerPreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
