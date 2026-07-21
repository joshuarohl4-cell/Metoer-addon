package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class FastMineV1 extends Module {
    public static FastMineV1 INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<SpeedPreset> speedPreset = sgGeneral.add(new EnumSetting.Builder<SpeedPreset>()
        .name("speed")
        .description("Mining speed multiplier")
        .defaultValue(SpeedPreset.Haste2)
        .build()
    );

    public final Setting<Double> customSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("custom-speed")
        .description("Custom mining speed multiplier")
        .defaultValue(2.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .visible(() -> speedPreset.get() == SpeedPreset.Custom)
        .build()
    );

    public final Setting<ServerPreset> serverPreset = sgGeneral.add(new EnumSetting.Builder<ServerPreset>()
        .name("server")
        .description("Auto-configure for specific server")
        .defaultValue(ServerPreset.DonutSMP)
        .onChanged(this::applyServerPreset)
        .build()
    );

    private float effectiveSpeed = 2.0f;

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Mine blocks faster.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        INSTANCE = this;
        updateSpeed();
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
    }

    private void updateSpeed() {
        switch (speedPreset.get()) {
            case Haste1 -> effectiveSpeed = 1.5f;
            case Haste2 -> effectiveSpeed = 2.0f;
            case Haste3 -> effectiveSpeed = 3.0f;
            case Haste5 -> effectiveSpeed = 5.0f;
            case Haste10 -> effectiveSpeed = 8.0f;
            case Custom -> effectiveSpeed = customSpeed.get().floatValue();
        }
    }

    private void applyServerPreset(ServerPreset preset) {
        switch (preset) {
            case DonutSMP -> speedPreset.set(SpeedPreset.Haste2);
            case EuropeSMP -> speedPreset.set(SpeedPreset.Haste2);
            case Hypixel -> speedPreset.set(SpeedPreset.Haste1);
            case Vanilla -> speedPreset.set(SpeedPreset.Haste5);
            case Any -> {}
        }
        updateSpeed();
    }

    public float getEffectiveSpeed() {
        if (speedPreset.get() == SpeedPreset.Custom) {
            return customSpeed.get().floatValue();
        }
        return effectiveSpeed;
    }

    public enum SpeedPreset {
        Haste1("Haste 1", "1.5x boost (safest)"),
        Haste2("Haste 2", "2x boost (recommended)"),
        Haste3("Haste 3", "3x boost"),
        Haste5("Haste 5", "5x boost"),
        Haste10("Haste 10", "8x boost (risky)"),
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
        DonutSMP("Donut SMP", "Safe settings"),
        EuropeSMP("Europe SMP", "Safe settings"),
        Hypixel("Hypixel", "Very safe settings"),
        Vanilla("Vanilla", "Maximum speed");

        private final String name;
        private final String description;

        ServerPreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
