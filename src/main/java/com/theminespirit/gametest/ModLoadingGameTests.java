package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.net.BuildFingerprint;
import com.theminespirit.neoforge.net.ModProtocolHandshake;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModLoadingGameTests {
    private ModLoadingGameTests() {}

    @GameTest(template = "empty")
    public static void modList_ServerStarted_ContainsTheMineSpirit(GameTestHelper helper) {
        helper.assertTrue(
                ModList.get().isLoaded(ModIdentity.MOD_ID),
                "Мод " + ModIdentity.MOD_ID + " не загружен сервером: проверь @Mod и neoforge.mods.toml");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protocol_Registration_HasVersion(GameTestHelper helper) {
        helper.assertTrue(
                ModProtocolHandshake.registered(),
                "Сетевой протокол мода не зарегистрирован: старый клиент снова дойдёт до set_entity_data и упадёт");
        helper.assertFalse(
                BuildFingerprint.current().isBlank(),
                "Версия сборки пустая: сверка протокола не отличит старый клиент от нового");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void command_Version_AvailableWithoutOperatorRights(GameTestHelper helper)
            throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack player =
                helper.getLevel().getServer().createCommandSourceStack().withPermission(0);

        int result = dispatcher.execute("spirit version", player);
        helper.assertTrue(result == 1, "Игрок без прав оператора обязан получить версию: /spirit version открыт всем");

        CommandNode<CommandSourceStack> spirit = dispatcher.getRoot().getChild("spirit");
        helper.assertTrue(
                !spirit.getChild("board").canUse(player),
                "Остальные подкоманды /spirit обязаны требовать права оператора: открытой осталась только version");
        helper.assertTrue(
                spirit.getChild("version").canUse(player),
                "Версия обязана быть доступна без прав оператора: canUse отклонил открытую команду");

        helper.assertTrue(
                BuildFingerprint.version().matches("\\d+\\.\\d+\\.[1-9]\\d*"),
                "Версия в команде обязана быть вида 0.1.N, получено: " + BuildFingerprint.version());
        helper.succeed();
    }
}
