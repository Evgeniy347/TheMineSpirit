package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritCommandGameTests {
    private SpiritCommandGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void commandGoal_OnSelectedSpirit_StartsAndCancelsTask(GameTestHelper helper)
            throws CommandSyntaxException {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withEntity(spirit);
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        helper.assertTrue(dispatcher.getRoot().getChild("spirit") != null,
                "Команда /spirit обязана быть зарегистрирована на сервере: без неё оператор не получит канал управления духом");

        int accepted = dispatcher.execute("spirit goal @s следуй за мной", source);
        helper.assertTrue(accepted == 1,
                "Команда goal обязана вернуть число принявших цель духов: планировщик отклонил распознаваемую цель, результат="
                        + accepted);
        helper.assertTrue(spirit.taskStatus().filter(s -> s == RunStatus.RUNNING).isPresent(),
                "После /spirit goal дух обязан быть в состоянии «выполняется»: команда не запустила распланированную задачу");

        int rejected = dispatcher.execute("spirit goal @s сруби дерево", source);
        helper.assertTrue(rejected == 0,
                "Цель, чья схема не реализована в каталоге духа (scan/chop_tree отсутствуют), обязана вернуть 0 и не "
                        + "заменить выполняемую задачу: результат=" + rejected);
        helper.assertTrue(spirit.taskStatus().filter(s -> s == RunStatus.RUNNING).isPresent(),
                "Отклонённая команда goal не должна трогать уже выполняемую задачу: статус духа изменился");

        int cancelled = dispatcher.execute("spirit cancel @s", source);
        helper.assertTrue(cancelled == 1,
                "Команда cancel обязана вернуть число духов с отменённой задачей: результат=" + cancelled);
        helper.assertTrue(spirit.taskStatus().filter(s -> s == RunStatus.CANCELLED).isPresent(),
                "После /spirit cancel задача духа обязана считаться отменённой: команда не остановила задачу");
        helper.succeed();
    }
}