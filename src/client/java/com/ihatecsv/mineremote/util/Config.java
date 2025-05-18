package com.ihatecsv.mineremote.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "mineremote.json");

    // defaults
    public int port = 8765;
    public String bindAddress = "127.0.0.1";
    public int updateMs = 200;
    public int iconSizePx = 256;
    public boolean allowCommands = true;
    public boolean debug = false;

    private static Config INSTANCE;

    public static Config get() { return INSTANCE == null ? load() : INSTANCE; }

    private static Config load() {
        try {
            if (Files.notExists(FILE)) save(new Config());
            JsonObject json = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            INSTANCE = GSON.fromJson(json, Config.class);
        } catch (Exception e) {
            System.err.println("[MineRemote] Failed to load config, using defaults: " + e);
            INSTANCE = new Config();
        }
        return INSTANCE;
    }

    private static void save(Config cfg) throws IOException {
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, GSON.toJson(cfg));
    }
}
