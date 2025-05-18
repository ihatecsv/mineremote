package com.ihatecsv.mineremote.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class WebServer {
    private static final long AWAIT_MS = 200;
    private static final Gson GSON = new Gson();

    public static <T> T await(Callable<T> task) {
        CompletableFuture<T> fut = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                fut.complete(task.call());
            } catch (Exception e) {
                fut.complete(null);
            }
        });
        try {
            return fut.get(AWAIT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonObject readJsonBody(HttpExchange ex) {
        try (InputStreamReader r = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, JsonObject.class);
        } catch (IOException e) {
            return null;
        }
    }

    public static void respondJson(HttpExchange ex, JsonObject obj) throws IOException {
        byte[] data = obj.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    public static void respond(HttpExchange ex, int code, String txt) throws IOException {
        byte[] data = txt.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
