package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModLoadingGameTests {
    private ModLoadingGameTests() {
    }

    @GameTest(template = "empty")
    public static void modList_ServerStarted_ContainsTheMineSpirit(GameTestHelper helper) {
        helper.assertTrue(
                ModList.get().isLoaded(ModIdentity.MOD_ID),
                "Мод " + ModIdentity.MOD_ID + " не загружен сервером: проверь @Mod и neoforge.mods.toml");
        helper.succeed();
    }
}
