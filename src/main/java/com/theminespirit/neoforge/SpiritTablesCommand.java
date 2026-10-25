package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableStatus;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class SpiritTablesCommand {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private SpiritTablesCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("tables").executes(context -> list(context.getSource())));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Таблицы данных мода (источник, время, хэш):"), false);
        int errors = 0;
        for (TableHolder<?> holder : DataTables.INSTANCE.registry().holders()) {
            TableStatus status = holder.status();
            String line = holder.id() + ": " + status.source().label() + ", " + TIME.format(status.loadedAt())
                    + ", хэш " + status.hash() + ", " + status.origin();
            if (status.failed()) {
                errors++;
                String problem = line + " — ОШИБКА: " + status.error();
                source.sendFailure(Component.literal(problem));
            } else {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        int total = DataTables.INSTANCE.registry().holders().size();
        int failed = errors;
        source.sendSuccess(
                () -> Component.literal("Всего таблиц: " + total + ", с ошибкой: " + failed
                        + (failed == 0 ? ". Данные применяются на лету." : ". Сломанные остались в прежней версии.")),
                false);
        return total;
    }
}
