package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Homemeta extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> homeNumber = sgGeneral.add(new IntSetting.Builder()
        .name("home-number")
        .description("Which home slot to use.")
        .defaultValue(3)
        .min(1)
        .sliderMin(1)
        .sliderMax(10)
        .build()
    );

    public Homemeta() {
        super(AddonTemplate.CATEGORY, "home-meta", "Runs my home reset sequence.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }
        
        info("Home meta activated! Use /home " + homeNumber.get() + " command manually.");
    }
}
