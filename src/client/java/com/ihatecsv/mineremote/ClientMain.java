package com.ihatecsv.mineremote;

import com.google.gson.*;
import com.ihatecsv.mineremote.util.Config;
import com.ihatecsv.mineremote.util.ImageRenderer;
import com.ihatecsv.mineremote.util.Inventory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.ihatecsv.mineremote.util.Inventory.*;
import static com.ihatecsv.mineremote.util.WebServer.*;

public class ClientMain implements ClientModInitializer {
	private static final String HTML_PATH = "/assets/mineremote/index.html";
	private static final Config C = Config.get();
	private static final int OUTSIDE = -999;
	private static final Map<String, byte[]> ICON_CACHE = new ConcurrentHashMap<>();

	private static final List<SSEClient> SSE_CLIENTS = new CopyOnWriteArrayList<>();
	private static final ScheduledExecutorService PUSH_EXEC =
			Executors.newSingleThreadScheduledExecutor();
	private static volatile JsonObject LAST_INV = null;

	@Override
	public void onInitializeClient() {
		Util.getMainWorkerExecutor().execute(ClientMain::startHttp);
	}

	private static void startHttp() {
		try {
			HttpServer s = HttpServer.create(new InetSocketAddress(C.bindAddress, C.port), 0);
			s.createContext("/", ClientMain::root);
			s.createContext("/api/inventory", ClientMain::inventory);
			s.createContext("/api/click", ClientMain::click);
			s.createContext("/api/toss", ClientMain::toss);
			s.createContext("/api/command", ClientMain::command);
			s.createContext("/api/events", ClientMain::events);
			s.createContext("/icon", ClientMain::icon);
			s.setExecutor(Executors.newSingleThreadExecutor());
			s.start();
			PUSH_EXEC.scheduleAtFixedRate(ClientMain::pushUpdates, 0, C.updateMs, TimeUnit.MILLISECONDS);
			System.out.println("[MineRemote] http://" + C.bindAddress + ":" + C.port);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void root(HttpExchange ex) throws IOException {
		if (!ex.getRequestMethod().equals("GET")) {
			respond(ex, 405, "");
			return;
		}
		try (InputStream in = ClientMain.class.getResourceAsStream(HTML_PATH)) {
			if (in == null) {
				respond(ex, 404, "index.html not found");
				return;
			}
			byte[] data = in.readAllBytes();
			ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
			ex.sendResponseHeaders(200, data.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(data);
			}
		}
	}

	private static void inventory(HttpExchange ex) throws IOException {
		if (!ex.getRequestMethod().equals("GET")) {
			respond(ex, 405, "");
			return;
		}
		respondJson(ex, buildInvJson());
	}

	private static void click(HttpExchange ex) throws IOException {
		if (!ex.getRequestMethod().equals("POST")) {
			respond(ex, 405, "");
			return;
		}
		JsonObject body = readJsonBody(ex);
		if (body == null || !body.has("slot")) {
			respond(ex, 400, "bad body");
			return;
		}
		int invSlot = body.get("slot").getAsInt();
		int mouseButton = body.has("button") ? body.get("button").getAsInt() : 0;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			respond(ex, 500, "no player");
			return;
		}
		int containerSlot = invToContainer(invSlot);
		if (containerSlot < 0) {
			respond(ex, 400, "bad slot");
			return;
		}
		JsonObject reply = await(() -> {
			if (mc.interactionManager != null) {
				mc.interactionManager.clickSlot(
						mc.player.currentScreenHandler.syncId,
						containerSlot,
						mouseButton,
						SlotActionType.PICKUP,
						mc.player);
			}
			return buildInvJson();
		});
		LAST_INV = reply;
		respondJson(ex, reply);
	}

	private static void toss(HttpExchange ex) throws IOException {
		if (!ex.getRequestMethod().equals("POST")) {
			respond(ex, 405, "");
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			respond(ex, 500, "no player");
			return;
		}
		JsonObject reply = await(() -> {
			if (mc.interactionManager != null) {
				mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
						OUTSIDE, 0, SlotActionType.PICKUP, mc.player);
			}
			return buildInvJson();
		});
		LAST_INV = reply;
		respondJson(ex, reply);
	}

	private static void command(HttpExchange ex) throws IOException {
		if (!Config.get().allowCommands) {
			respond(ex, 403, "command endpoint disabled");
			return;
		}
		if (!ex.getRequestMethod().equals("POST")) {
			respond(ex, 405, "");
			return;
		}
		JsonObject body = readJsonBody(ex);
		if (body == null || !body.has("cmd")) {
			respond(ex, 400, "bad body");
			return;
		}
		String cmd = body.get("cmd").getAsString();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			respond(ex, 500, "no player");
			return;
		}
		JsonObject reply = await(() -> {
			mc.player.networkHandler.sendChatCommand(cmd);
			return buildInvJson();
		});
		LAST_INV = reply;
		respondJson(ex, reply);
	}

	private static void icon(HttpExchange ex) throws IOException {
		if (!ex.getRequestMethod().equals("GET")) {
			respond(ex, 405, "");
			return;
		}
		String raw = ex.getRequestURI().getPath().substring("/icon/".length());
		if (!raw.endsWith(".png") || !raw.contains("/")) {
			respond(ex, 400, "");
			return;
		}
		raw = raw.substring(0, raw.length() - 4);
		String id = raw.replace('/', ':');
		byte[] png = ICON_CACHE.computeIfAbsent(id, key -> await(() -> renderIconPng(key)));
		if (png == null) {
			respond(ex, 404, "");
			return;
		}
		ex.getResponseHeaders().add("Content-Type", "image/png");
		ex.sendResponseHeaders(200, png.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(png);
		}
	}

	private static void events(HttpExchange ex) throws IOException {
		if (!ex.getRequestMethod().equals("GET")) {
			respond(ex, 405, "");
			return;
		}
		ex.getResponseHeaders().add("Content-Type", "text/event-stream");
		ex.getResponseHeaders().add("Cache-Control", "no-cache");
		ex.getResponseHeaders().add("Connection", "keep-alive");
		ex.sendResponseHeaders(200, 0);
		SSEClient client = new SSEClient(ex);
		SSE_CLIENTS.add(client);
		JsonObject init = await(Inventory::buildInvJson);
		if (init != null) {
			LAST_INV = init;
			client.send(("data: " + init.toString() + "\n\n").getBytes(StandardCharsets.UTF_8));
		}
	}

	private static void pushUpdates() {
		JsonObject cur = await(Inventory::buildInvJson);
		if (cur == null) return;
		if (LAST_INV != null && LAST_INV.equals(cur)) return;
		byte[] msg = ("data: " + cur.toString() + "\n\n").getBytes(StandardCharsets.UTF_8);
		for (SSEClient c : SSE_CLIENTS) {
			try {
				c.send(msg);
			} catch (IOException e) {
				c.close();
				SSE_CLIENTS.remove(c);
			}
		}
		LAST_INV = cur;
	}

	private static class SSEClient {
		final HttpExchange ex;
		final OutputStream out;

		SSEClient(HttpExchange ex) {
			this.ex = ex;
			this.out = ex.getResponseBody();
		}

		void send(byte[] bytes) throws IOException {
			out.write(bytes);
			out.flush();
		}

		void close() {
			try {
				out.close();
			} catch (IOException ignored) {
			}
			try {
				ex.close();
			} catch (Exception ignored) {
			}
		}
	}

	private static byte[] renderIconPng(String itemId) {
		if (C.debug) System.out.println("renderIconPng ▸ request for " + itemId);
		Item item = Registries.ITEM.get(new Identifier(itemId));
		if (item == null || item == Items.AIR) {
			System.out.println("renderIconPng ✖ unknown item id " + itemId);
			return null;
		}
		ItemStack stack = new ItemStack(item);
		try (ImageRenderer ir = new ImageRenderer(Config.get().iconSizePx)) {
			Path tmp = Files.createTempFile("icon_", ".png");
			ir.captureRender(tmp, () -> {
				MinecraftClient mc = MinecraftClient.getInstance();
				DrawContext d = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());
				d.drawItem(stack, 0, 0);
				d.draw();
			});
			byte[] data = Files.readAllBytes(tmp);
			Files.deleteIfExists(tmp);
			if (C.debug) System.out.println("renderIconPng ✔ encoded " + data.length + " bytes");
			return data;
		} catch (Exception e) {
			System.out.println("renderIconPng ✖ " + e.getMessage());
			return null;
		}
	}
}
