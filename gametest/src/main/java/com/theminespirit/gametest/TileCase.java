package com.theminespirit.gametest;

import com.theminespirit.core.farming.FarmTile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

record TileCase(String name, Block ground, BlockState above, FarmTile expected) {}
