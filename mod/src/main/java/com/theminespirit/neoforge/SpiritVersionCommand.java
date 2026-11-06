package com.theminespirit.neoforge;

import com.mojang.brigadier.context.CommandContext;
import com.theminespirit.neoforge.net.BuildFingerprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class SpiritVersionCommand {
    private SpiritVersionCommand() {}

    static int version(CommandContext<CommandSourceStack> context) {
        String version = BuildFingerprint.version();
        context.getSource().sendSuccess(() -> Component.literal("The Mine Spirit " + version), false);
        return 1;
    }
}
