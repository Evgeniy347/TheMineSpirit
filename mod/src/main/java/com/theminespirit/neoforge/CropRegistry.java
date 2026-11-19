package com.theminespirit.neoforge;

import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.farming.api.CropKind;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class CropRegistry {

    private CropRegistry() {}

    public static Block block(CropKind kind) {
        return lookup(BuiltInRegistries.BLOCK, kind.blockId(), kind, "блок посева");
    }

    public static Item plantItem(CropKind kind) {
        return lookup(BuiltInRegistries.ITEM, NamespacedId.MINECRAFT + kind.plantItem(), kind, "посадочный предмет");
    }

    public static Item produceItem(CropKind kind) {
        return lookup(BuiltInRegistries.ITEM, NamespacedId.MINECRAFT + kind.produceItem(), kind, "предмет урожая");
    }

    public static Block fruitBlock(CropKind kind) {
        return lookup(BuiltInRegistries.BLOCK, kind.fruitBlockId(), kind, "блок плода");
    }

    public static Block supportBlock(CropKind kind) {
        return lookup(BuiltInRegistries.BLOCK, kind.supportBlockId(), kind, "блок опоры");
    }

    public static Set<Block> blocks(CropKind filter) {
        return Arrays.stream(CropKind.values())
                .filter(kind -> kind != CropKind.ANY && (filter == CropKind.ANY || kind == filter))
                .map(CropRegistry::block)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static <T> T lookup(Registry<T> registry, String id, CropKind kind, String what) {
        return registry.getOptional(ResourceLocation.parse(id))
                .orElseThrow(() -> new IllegalStateException("В реестре нет " + what + " " + id + " для вида " + kind
                        + ": id в CropKind неверен для версии игры, " + "сверь его с реестром"));
    }
}
