package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.Stations;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public enum StationType {
    CRAFTING_TABLE(Stations.CRAFTING_TABLE, Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE, false),
    FURNACE(Stations.FURNACE, Blocks.FURNACE, Items.FURNACE, true);

    private final String id;
    private final Block block;
    private final Item item;
    private final boolean facesPlacer;

    StationType(String id, Block block, Item item, boolean facesPlacer) {
        this.id = id;
        this.block = block;
        this.item = item;
        this.facesPlacer = facesPlacer;
    }

    public static Optional<StationType> byId(String id) {
        return Arrays.stream(values()).filter(type -> type.id.equals(ItemIds.normalize(id))).findFirst();
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

    public BlockState placedState(Direction towardPlacer) {
        BlockState state = block.defaultBlockState();
        return facesPlacer ? state.setValue(HorizontalDirectionalBlock.FACING, towardPlacer) : state;
    }
}
