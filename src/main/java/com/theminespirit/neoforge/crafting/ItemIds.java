package com.theminespirit.neoforge.crafting;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ItemIds {
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private ItemIds() {}

    public static String of(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return DEFAULT_NAMESPACE.equals(key.getNamespace()) ? key.getPath() : key.toString();
    }

    public static Optional<Item> item(String id) {
        String qualified = id.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + id : id;
        ResourceLocation location = ResourceLocation.tryParse(qualified);
        if (location == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(location).filter(item -> item != Items.AIR);
    }

    public static String normalize(String id) {
        String prefix = DEFAULT_NAMESPACE + ":";
        return id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }
}
