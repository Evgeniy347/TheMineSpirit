package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class SpiritPresetsCommand {
    private SpiritPresetsCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("presets").executes(context -> list(context.getSource())));
    }

    private static int list(CommandSourceStack source) {
        PresetLibrary library = DataTables.INSTANCE.presets().current();
        TableStatus status = DataTables.INSTANCE.presets().status();
        source.sendSuccess(
                () -> Component.literal("Библиотека построек: " + library.size() + " пресетов ("
                        + status.source().label() + ", " + status.origin() + ")"),
                false);
        for (Preset preset : library.all()) {
            String line = preset.id() + ": " + preset.name() + "; этап " + preset.stage() + " ("
                    + preset.type().label() + "); " + preset.sizeX() + "×" + preset.sizeY() + "×" + preset.sizeZ()
                    + "; вход [" + preset.entry().x() + "," + preset.entry().y() + ","
                    + preset.entry().z() + "]; "
                    + "материалы " + preset.materials();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (status.failed()) {
            source.sendFailure(Component.literal("Библиотека построек с ошибкой: " + status.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Библиотека построек прочитана без ошибок."), false);
        return library.size();
    }
}
