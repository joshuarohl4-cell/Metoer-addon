package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class VelocityPlus extends Module {
    public enum VelocityMode {
        Cancel,
        Skip,
        Reduce
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<VelocityMode> mode = sgGeneral.add(new EnumSetting.Builder<VelocityMode>()
        .name("mode")
        .description("Velocity bypass mode")
        .defaultValue(VelocityMode.Reduce)
        .build()
    );

    private final Setting<Integer> horizontal = sgGeneral.add(new IntSetting.Builder()
        .name("horizontal")
        .description("Horizontal velocity percentage (0 = full cancel)")
        .defaultValue(0)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(() -> mode.get() == VelocityMode.Reduce)
        .build()
    );

    private final Setting<Integer> vertical = sgGeneral.add(new IntSetting.Builder()
        .name("vertical")
        .description("Vertical velocity percentage (0 = full cancel)")
        .defaultValue(0)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(() -> mode.get() == VelocityMode.Reduce)
        .build()
    );

    public VelocityPlus() {
        super(AddonTemplate.CATEGORY, "velocity+", "Bypass velocity for anti-cheats");
    }

    @Override
    public void onDeactivate() {
    }
}
