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

        // === ESP MODULES (Base Finding) ===
        Modules.get().add(new SpawnerESP());          // Green ESP boxes on spawners
        Modules.get().add(new ChestESP());            // ESP for chests, barrels, shulkers
        Modules.get().add(new RedstoneESP());         // ESP for redstone components
        Modules.get().add(new BedrockVoidESP());     // Find voids in bedrock
        Modules.get().add(new SusChunk());            // Detect suspicious chunks
        
        // === LEGACY MODULES ===
        Modules.get().add(new ChunkFinder());         // Chunk scanning
        Modules.get().add(new SpawnerChunks());       // Chunk spawner highlight
        Modules.get().add(new SpawnerFinder());       // Spawner finder

        // === UTILITY MODULES ===
        Modules.get().add(new Homemeta());
        Modules.get().add(new ResetHome());
        Modules.get().add(new RenderDistanceToggle());

        // === MINING MODULES ===
        Modules.get().add(new FastMineV1());          // Fast mine with mixin
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
