package com.example.addon;

import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Example");
    public static final HudGroup HUD_GROUP = new HudGroup("Example");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Addon Template");

        // === STORAGE ESP (No packets - uses server data only) ===
        Modules.get().add(new StorageESP());        // Highlights all storage blocks - NO PACKET MANIPULATION
        
        // === BASE FINDING (Donut SMP / GrimAC Servers) ===
        Modules.get().add(new BedrockVoidESP());     // Find voids in bedrock - WORKS!
        Modules.get().add(new SpawnerESP());         // Highlight spawners with green ESP
        Modules.get().add(new BlockNotifier());      // Find specific blocks
        
        // === UTILITY ===
        Modules.get().add(new Homemeta());
        Modules.get().add(new ResetHome());
        Modules.get().add(new RenderDistanceToggle());

        // === MINING ===
        Modules.get().add(new FastMineV1());          // Fast mine
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
