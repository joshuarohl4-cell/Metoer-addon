package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;

public class FastMineV1 extends Module {
    public static FastMineV1 INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Anti-Cheat Bypass");

    // Mining speed presets (SAFER VALUES)
    public final Setting<SpeedPreset> speedPreset = sgGeneral.add(new EnumSetting.Builder<SpeedPreset>()
        .name("speed")
        .description("Mining speed - lower = safer from detection")
        .defaultValue(SpeedPreset.Haste3)
        .onChanged(preset -> applySpeedPreset(preset))
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

    // Anti-cheat bypass settings
    public final Setting<Boolean> antiCheatMode = sgBypass.add(new BoolSetting.Builder()
        .name("anti-cheat-mode")
        .description("Enables detection bypass techniques")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> variance = sgBypass.add(new IntSetting.Builder()
        .name("random-variance")
        .description("Adds random variance to speed (0-50%) to look more natural")
        .defaultValue(25)
        .min(0)
        .max(50)
        .sliderRange(0, 50)
        .visible(antiCheatMode::get)
        .build()
    );

    public final Setting<Boolean> burstMode = sgBypass.add(new BoolSetting.Builder()
        .name("burst-mode")
        .description("Mine in bursts with pauses to avoid constant fast mining")
        .defaultValue(true)
        .visible(antiCheatMode::get)
        .build()
    );

    public final Setting<Integer> burstTicks = sgBypass.add(new IntSetting.Builder()
        .name("burst-ticks")
        .description("How many ticks to mine fast in each burst")
        .defaultValue(8)
        .min(1)
        .max(50)
        .sliderRange(1, 50)
        .visible(() -> burstMode.get() && antiCheatMode.get())
        .build()
    );

    public final Setting<Integer> pauseTicks = sgBypass.add(new IntSetting.Builder()
        .name("pause-ticks")
        .description("How many ticks to pause between bursts")
        .defaultValue(15)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .visible(() -> burstMode.get() && antiCheatMode.get())
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
    private float effectiveSpeed = 2.0f;
    private float currentSpeed = 1.0f;
    private int burstTimer = 0;
    private int pauseTimer = 0;
    private boolean isPaused = false;
    private Timer meteorTimer = null;
    private boolean timerEnabled = false;

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Mine blocks faster with anti-cheat bypass techniques.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (INSTANCE != this) INSTANCE = this;
        applySpeedPreset(speedPreset.get());
        applyServerPreset(serverPreset.get());
        meteorTimer = Modules.get().get(Timer.class);
        resetState();
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
        if (meteorTimer != null) {
            try {
                meteorTimer.setOverride(1.0);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void resetState() {
        burstTimer = 0;
        pauseTimer = 0;
        isPaused = false;
        currentSpeed = 1.0f;
    }

    private void applySpeedPreset(SpeedPreset preset) {
        switch (preset) {
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
                antiCheatMode.set(true);
                variance.set(30);
                burstMode.set(true);
                burstTicks.set(10);
                pauseTicks.set(20);
                applySpeedPreset(SpeedPreset.Haste2);
                break;
            case EuropeSMP:
                speedPreset.set(SpeedPreset.Haste2);
                antiCheatMode.set(true);
                variance.set(30);
                burstMode.set(true);
                burstTicks.set(10);
                pauseTicks.set(20);
                applySpeedPreset(SpeedPreset.Haste2);
                break;
            case Hypixel:
                speedPreset.set(SpeedPreset.Haste1);
                antiCheatMode.set(true);
                variance.set(40);
                burstMode.set(true);
                burstTicks.set(5);
                pauseTicks.set(25);
                applySpeedPreset(SpeedPreset.Haste1);
                break;
            case Vanilla:
                speedPreset.set(SpeedPreset.Haste5);
                antiCheatMode.set(false);
                applySpeedPreset(SpeedPreset.Haste5);
                break;
            case Any:
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (meteorTimer == null) {
            meteorTimer = Modules.get().get(Timer.class);
        }

        if (meteorTimer == null) return;

        if (!antiCheatMode.get()) {
            // No anti-cheat bypass, use constant speed
            currentSpeed = effectiveSpeed;
            timerEnabled = true;
        } else if (!burstMode.get()) {
            // Just apply variance without bursting
            float varianceAmount = (float) (Math.random() * variance.get() / 100.0);
            currentSpeed = effectiveSpeed * (1.0f + varianceAmount);
            timerEnabled = true;
        } else {
            // Burst mode - alternate between fast mining and normal
            if (!isPaused) {
                burstTimer++;
                // Apply speed with variance
                float varianceAmount = (float) (Math.random() * variance.get() / 100.0);
                currentSpeed = effectiveSpeed * (1.0f + varianceAmount);
                timerEnabled = true;

                if (burstTimer >= burstTicks.get()) {
                    isPaused = true;
                    pauseTimer = 0;
                    currentSpeed = 1.0f;
                    timerEnabled = false;
                }
            } else {
                pauseTimer++;
                currentSpeed = 1.0f;
                timerEnabled = false;

                if (pauseTimer >= pauseTicks.get()) {
                    isPaused = false;
                    burstTimer = 0;
                    currentSpeed = effectiveSpeed;
                    timerEnabled = true;
                }
            }
        }

        // Apply timer multiplier
        try {
            if (timerEnabled && currentSpeed > 1.0f) {
                meteorTimer.setOverride(currentSpeed);
            } else {
                meteorTimer.setOverride(1.0);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    public float getEffectiveSpeed() {
        return currentSpeed;
    }

    public boolean isMiningFast() {
        return timerEnabled && currentSpeed > 1.0f;
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
