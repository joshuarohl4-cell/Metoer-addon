package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class RenderDistanceToggle extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> lowDistance = sgGeneral.add(new IntSetting.Builder()
        .name("low-distance")
        .description("Render distance when below reset Y level.")
        .defaultValue(2)
        .range(2, 32)
        .sliderRange(2, 32)
        .build()
    );

    private final Setting<Integer> highDistance = sgGeneral.add(new IntSetting.Builder()
        .name("high-distance")
        .description("Render distance when above reset Y level.")
        .defaultValue(14)
        .range(2, 32)
        .sliderRange(2, 32)
        .build()
    );

    private final Setting<Integer> resetY = sgGeneral.add(new IntSetting.Builder()
        .name("reset-y")
        .description("Y level to toggle render distance at.")
        .defaultValue(-10)
        .min(-64)
        .max(320)
        .sliderRange(-64, 320)
        .build()
    );

    private boolean armed = false;
    private int stage = 0;
    private int timer = 0;

    public RenderDistanceToggle() {
        super(AddonTemplate.CATEGORY, "render-distance-toggle", "Automatically refreshes chunks when going below the configured Y level.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.options == null) return;

        if (mc.player.getY() < resetY.get()) {
            if (!armed) {
                armed = true;
                stage = 0;
            }
        } else {
            if (armed) {
                armed = false;
                stage = 0;
            }
            return;
        }

        switch (stage) {
            case 0 -> {
                // Set low render distance
                mc.options.getViewDistance().setValue(lowDistance.get());
                timer = 0;
                stage = 1;
            }
            case 1 -> {
                timer++;
                if (timer >= 10) {
                    // Set high render distance
                    mc.options.getViewDistance().setValue(highDistance.get());
                    timer = 0;
                    stage = 2;
                }
            }
            case 2 -> {
                timer++;
                if (timer >= 10) {
                    // Return to low
                    mc.options.getViewDistance().setValue(lowDistance.get());
                    timer = 0;
                    stage = 1;
                }
            }
        }
    }
}
