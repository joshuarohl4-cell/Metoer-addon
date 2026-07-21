package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class FastMineV1 extends Module {
    public static FastMineV1 INSTANCE;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Base mining speed multiplier. Higher values make mining noticeably faster.")
        .defaultValue(12.0)
        .range(1.0, 100.0)
        .build()
    );

    public final Setting<Boolean> sprintBoost = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint-boost")
        .description("Apply a small extra boost while sprinting and moving.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> sprintMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("sprint-multiplier")
        .description("Extra mining speed multiplier while sprinting or moving.")
        .defaultValue(3.0)
        .range(1.0, 10.0)
        .build()
    );

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Increase mining speed in a more server-friendly way.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (INSTANCE != this) INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
    }
}
