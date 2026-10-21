package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.net.BuildFingerprint;
import com.theminespirit.neoforge.net.ModProtocolHandshake;
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
}
