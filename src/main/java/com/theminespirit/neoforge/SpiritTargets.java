package com.theminespirit.neoforge;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;

final class SpiritTargets {
    private SpiritTargets() {
    }

    static List<SpiritEntity> selected(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SpiritEntity> spirits = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(context, "spirits")) {
            if (entity instanceof SpiritEntity spirit) {
                spirits.add(spirit);
            }
        }
        return spirits;
    }
}
