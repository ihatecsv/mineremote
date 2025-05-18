/*
 * This file contains code adapted from ComputerCraft, © Daniel Ratcliffe,
 * licensed under the ComputerCraft Public License (CCPL).
 * See: /LICENSE-CCPL
 */

package com.ihatecsv.mineremote.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.texture.NativeImage;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImageRenderer implements AutoCloseable {
    public final int width;
    public final int height;

    private final Framebuffer framebuffer;
    private final NativeImage image;

    public ImageRenderer(int size) {
        this.width = this.height = size;
        framebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        image = new NativeImage(width, height, MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.setClearColor(0, 0, 0, 0);
        framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
    }

    public void captureRender(Path output, Runnable render) throws IOException {
        Files.createDirectories(output.getParent());

        framebuffer.setClearColor(0, 0, 0, 0);
        framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.beginWrite(true);

        var oldProj = RenderSystem.getProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().identity().ortho(0, 16, 16, 0, 1000, 3000),
                VertexSorter.BY_Z
        );

        var mv = RenderSystem.getModelViewStack();
        mv.push();
        mv.loadIdentity();
        mv.translate(0.0f, 0.0f, -2000.0f);
        RenderSystem.applyModelViewMatrix();

        DiffuseLighting.enableGuiDepthLighting();
        BackgroundRenderer.clearFog();

        render.run();

        RenderSystem.setProjectionMatrix(oldProj, VertexSorter.BY_DISTANCE);
        RenderSystem.getModelViewStack().pop();
        RenderSystem.applyModelViewMatrix();

        framebuffer.endWrite();
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);

        framebuffer.beginRead();
        image.loadFromTextureImage(0, false);
        image.mirrorVertically();
        framebuffer.endRead();
        image.writeTo(output);
    }

    @Override
    public void close() {
        image.close();
        framebuffer.delete();
    }
}
