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
    private final SettingGroup sgTimer = settings.createGroup("Timer Bypass");

    // Enable timer-based bypass
    public final Setting<Boolean> useTimerBypass = sgTimer.add(new BoolSetting.Builder()
        .name("use-timer-bypass")
        .description("Use timer with recharge pattern to bypass anti-cheat (recommended)")
        .defaultValue(true)
        .build()
    );

    // Mining speed presets
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

    // Timer settings for bypass
    public final Setting<Double> timerMultiplier = sgTimer.add(new DoubleSetting.Builder()
        .name("timer-multiplier")
        .description("Timer speed when mining (higher = faster mining)")
        .defaultValue(3.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .visible(useTimerBypass::get)
        .build()
    );

    public final Setting<Integer> workTicks = sgTimer.add(new IntSetting.Builder()
        .name("work-ticks")
        .description("How many ticks to mine at high speed before recharge")
        .defaultValue(15)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .visible(useTimerBypass::get)
        .build()
    );

    public final Setting<Integer> rechargeTicks = sgTimer.add(new IntSetting.Builder()
        .name("recharge-ticks")
        .description("How many ticks to wait before next mining burst")
        .defaultValue(25)
        .min(1)
        .max(200)
        .sliderRange(1, 200)
        .visible(useTimerBypass::get)
        .build()
    );

    // Server specific presets
    public final Setting<ServerPreset> serverPreset = sgGeneral.add(new EnumSetting.Builder<ServerPreset>()
        .name("server")
        .description("Auto-configure for specific server type")
        .defaultValue(ServerPreset.DonutSMP)
        .onChanged(preset -> applyServerPreset(preset))
        .build()
    );

    // Internal state
    private float effectiveSpeed = 10.0f;
    private int workTimer = 0;
    private int rechargeTimer = 0;
    private boolean isRecharging = false;
    private Timer meteorTimer = null;

    public FastMineV1() {
        super(AddonTemplate.CATEGORY, "fast-mine-v1", "Mine blocks faster with anti-cheat bypass using timer patterns.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (INSTANCE != this) INSTANCE = this;
        applySpeedPreset(speedPreset.get());
        applyServerPreset(serverPreset.get());
        
        // Get the meteor Timer module
        meteorTimer = Modules.get().get(Timer.class);
        
        // Reset timers
        workTimer = 0;
        rechargeTimer = 0;
        isRecharging = false;
    }

    @Override
    public void onDeactivate() {
        if (INSTANCE == this) INSTANCE = null;
        
        // Reset timer if we modified it
        if (meteorTimer != null) {
            try {
                meteorTimer.setOverride(1.0);
            } catch (Exception e) {
                // Ignore
            }
        }
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
                useTimerBypass.set(true);
                timerMultiplier.set(2.5);
                workTicks.set(15);
                rechargeTicks.set(30);
                applySpeedPreset(SpeedPreset.Haste10);
                break;
            case EuropeSMP:
                speedPreset.set(SpeedPreset.Haste10);
                useTimerBypass.set(true);
                timerMultiplier.set(2.5);
                workTicks.set(15);
                rechargeTicks.set(30);
                applySpeedPreset(SpeedPreset.Haste10);
                break;
            case Hypixel:
                speedPreset.set(SpeedPreset.Haste5);
                useTimerBypass.set(true);
                timerMultiplier.set(2.0);
                workTicks.set(10);
                rechargeTicks.set(40);
                applySpeedPreset(SpeedPreset.Haste5);
                break;
            case Vanilla:
                speedPreset.set(SpeedPreset.Instant);
                useTimerBypass.set(false);
                applySpeedPreset(SpeedPreset.Instant);
                break;
            case Any:
                // Keep current settings
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (meteorTimer == null) {
            meteorTimer = Modules.get().get(Timer.class);
        }
        
        if (!useTimerBypass.get() || meteorTimer == null) {
            // Just use speed modifier, no timer bypass
            return;
        }

        // Timer bypass logic - work/recharge pattern
        if (!isRecharging) {
            // Working phase - set high timer
            workTimer++;
            if (workTimer >= workTicks.get()) {
                isRecharging = true;
                rechargeTimer = 0;
            }
            // Apply timer multiplier
            try {
                meteorTimer.setOverride(timerMultiplier.get());
            } catch (Exception e) {
                // Ignore
            }
        } else {
            // Recharging phase - normal timer
            rechargeTimer++;
            if (rechargeTimer >= rechargeTicks.get()) {
                isRecharging = false;
                workTimer = 0;
            }
            // Reset to normal
            try {
                meteorTimer.setOverride(1.0);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public float getEffectiveSpeed() {
        // Apply random variance to avoid detection
        float variance = (float) (Math.random() * 0.03 - 0.015); // ±1.5%
        return effectiveSpeed * (1.0f + variance);
    }

    public boolean isMiningAtHighSpeed() {
        return !isRecharging || !useTimerBypass.get();
    }

    public boolean isUsingTimerBypass() {
        return useTimerBypass.get();
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
        DonutSMP("Donut SMP", "Timer bypass + 10x speed"),
        EuropeSMP("Europe SMP", "Timer bypass + 10x speed"),
        Hypixel("Hypixel", "Timer bypass + 5x speed"),
        Vanilla("Vanilla", "Maximum speed, no bypass");

        private final String name;
        private final String description;

        ServerPreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
