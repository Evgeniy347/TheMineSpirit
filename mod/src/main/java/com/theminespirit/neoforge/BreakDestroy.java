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

    record Break(
            BlockPos pos,
            BlockState before,
            BlockEntity entity,
            ItemStack tool,
            boolean drops,
            boolean wears,
            String intent,
            PlotProtection.Ground ground) {}

    static Result after(SpiritEntity spirit, Level level, Break broken) {
        if (level instanceof ServerLevel server) {
            BreakNotice.broken(server, broken.pos(), broken.before(), spirit, broken.intent(), broken.ground());
        }
        if (!SpiritBlocks.destroy((ServerLevel) spirit.level(), broken.pos(), false, spirit, broken.intent())) {
            return new Result(false, "");
        }
        spirit.hunger().onBlockBroken();
        if (broken.drops()) {
            Block.dropResources(broken.before(), level, broken.pos(), broken.entity(), spirit, broken.tool());
        }
        if (!broken.tool().isEmpty() && broken.wears()) {
            String suffix = ToolSuffix.ofItem(
                    BuiltInRegistries.ITEM.getKey(broken.tool().getItem()).toString());
            int breaksBefore = spirit.toolBreaks();
            broken.tool().hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
            boolean toolBroke = broken.tool().isEmpty() || spirit.toolBreaks() > breaksBefore;
            return new Result(toolBroke, toolBroke ? suffix : "");
        }
        return new Result(false, "");
    }
}
