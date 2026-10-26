package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.TerrainRaster;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

final class TerrainTexture {
    private static final AtomicInteger NEXT = new AtomicInteger();
    private final ResourceLocation id;
    private final int width;
    private final int height;

    private TerrainTexture(ResourceLocation id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    static TerrainTexture of(TerrainRaster raster) {
        ResourceLocation id =
                ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "town_map_terrain_" + NEXT.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(id, create(raster));
        return new TerrainTexture(id, raster.width(), raster.height());
    }

    private static DynamicTexture create(TerrainRaster raster) {
        DynamicTexture texture = new DynamicTexture(raster.width(), raster.height(), false);
        writePixels(texture, raster);
        texture.upload();
        return texture;
    }

    private static NativeImage writePixels(DynamicTexture texture, TerrainRaster raster) {
        NativeImage image = texture.getPixels();
        if (image == null) {
            throw new IllegalStateException("Текстура карты " + raster.width() + "×" + raster.height()
                    + " не дала пикселей: без изображения местность не нарисовать");
        }
        int[] pixels = raster.pixels();
        for (int y = 0; y < raster.height(); y++) {
            for (int x = 0; x < raster.width(); x++) {
                image.setPixelRGBA(x, y, abgr(pixels[y * raster.width() + x]));
            }
        }
        return image;
    }

    ResourceLocation id() {
        return id;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }

    private static int abgr(int argb) {
        int alpha = argb >>> 24;
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        return alpha << 24 | blue << 16 | green << 8 | red;
    }
}
