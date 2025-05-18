package com.ihatecsv.mineremote.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;

public class Inventory {
    public static int invToContainer(int invSlot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
            Slot s = mc.player.currentScreenHandler.getSlot(i);
            if (s.inventory == mc.player.getInventory() && s.getIndex() == invSlot) return i;
        }
        return -1;
    }

    public static JsonObject buildInvJson() {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonArray slotsArr = new JsonArray();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (!st.isEmpty()) {
                JsonObject o = new JsonObject();
                o.addProperty("slot", i);
                o.addProperty("item", Registries.ITEM.getId(st.getItem()).toString());
                o.addProperty("count", st.getCount());
                NbtCompound tag = st.getNbt();
                o.addProperty("nbt", tag != null ? tag.toString() : "");
                slotsArr.add(o);
            }
        }
        ItemStack cur = mc.player.currentScreenHandler.getCursorStack();
        JsonObject carried = new JsonObject();
        if (!cur.isEmpty()) {
            carried.addProperty("item", Registries.ITEM.getId(cur.getItem()).toString());
            carried.addProperty("count", cur.getCount());
            NbtCompound tag = cur.getNbt();
            carried.addProperty("nbt", tag != null ? tag.toString() : "");
        } else {
            carried.addProperty("item", "");
            carried.addProperty("count", 0);
            carried.addProperty("nbt", "");
        }
        float health = mc.player.getHealth();
        float maxHealth = (float) mc.player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
        int armor = mc.player.getArmor();
        HungerManager hm = mc.player.getHungerManager();
        int hunger = hm.getFoodLevel();
        float saturation = hm.getSaturationLevel();
        int xpLevel = mc.player.experienceLevel;
        float xpProgress = mc.player.experienceProgress;
        int selSlot = mc.player.getInventory().selectedSlot;
        JsonObject root = new JsonObject();
        root.add("slots", slotsArr);
        root.add("carried", carried);
        root.addProperty("health", health);
        root.addProperty("maxHealth", maxHealth);
        root.addProperty("armor", armor);
        root.addProperty("hunger", hunger);
        root.addProperty("saturation", saturation);
        root.addProperty("expLevel", xpLevel);
        root.addProperty("expProgress", xpProgress);
        root.addProperty("sel", selSlot);
        return root;
    }
}
