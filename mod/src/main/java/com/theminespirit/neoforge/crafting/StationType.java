package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.Stations;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum StationType {
    CRAFTING_TABLE(Stations.CRAFTING_TABLE, Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE, "нужен верстак"),
    FURNACE(Stations.FURNACE, Blocks.FURNACE, Items.FURNACE, "нужна печь"),
    SMOKER(Stations.SMOKER, Blocks.SMOKER, Items.SMOKER, "нужна коптильня"),
    CAMPFIRE(Stations.CAMPFIRE, Blocks.CAMPFIRE, Items.CAMPFIRE, "нужен костёр"),
    BREWING_STAND("minecraft:brewing_stand", Blocks.BREWING_STAND, Items.BREWING_STAND, "нужна варочная стойка");

    private final String id;
    private final Block block;
    private final Item item;
    private final String needed;

    StationType(String id, Block block, Item item, String needed) {
        this.id = id;
        this.block = block;
        this.item = item;
        this.needed = needed;
    }

    public static Optional<StationType> byId(String id) {
        return Arrays.stream(values())
                .filter(type -> type.id.equals(ItemIds.normalize(id)))
                .findFirst();
    }

    public static Optional<StationType> byBlock(Block block) {
        return Arrays.stream(values())
                .filter(type -> type.block().equals(block))
                .findFirst();
    }

    public String id() {
        return id;
    }

    public Block block() {
        return block;
    }

    public Item item() {
        return item;
    }

    public String needed() {
        return needed;
    }
}
