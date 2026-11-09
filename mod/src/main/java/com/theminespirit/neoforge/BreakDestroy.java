package com.theminespirit.neoforge;

import com.theminespirit.core.mining.ToolSuffix;
import com.theminespirit.core.town.PlotProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class BreakDestroy {
    private BreakDestroy() {}

    record Result(boolean toolBroke, String suffix) {}

    static Result after(
            SpiritEntity spirit,
            Level level,
            BlockPos brokenPos,
            BlockState broken,
            BlockEntity blockEntity,
            ItemStack usedTool,
            boolean dropsAllowed,
            boolean wearsTool,
            String intent,
            PlotProtection.Ground ground) {
        if (level instanceof ServerLevel server) {
            BreakNotice.broken(server, brokenPos, broken, spirit, intent, ground);
        }
        level.destroyBlock(brokenPos, false, spirit);
        spirit.hunger().onBlockBroken();
        if (dropsAllowed) {
            Block.dropResources(broken, level, brokenPos, blockEntity, spirit, usedTool);
        }
        if (!usedTool.isEmpty() && wearsTool) {
            String suffix = ToolSuffix.ofItem(
                    BuiltInRegistries.ITEM.getKey(usedTool.getItem()).toString());
            int breaksBefore = spirit.toolBreaks();
            usedTool.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
            boolean toolBroke = usedTool.isEmpty() || spirit.toolBreaks() > breaksBefore;
            return new Result(toolBroke, toolBroke ? suffix : "");
        }
        return new Result(false, "");
    }
}
