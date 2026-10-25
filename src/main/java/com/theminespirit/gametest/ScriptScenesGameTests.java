package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «скрипты, команды, итоги»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScriptScenesGameTests {
    private ScriptScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_script_arena", timeoutTicks = 165232)
    public static void scriptScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena", "empty"),
                HotReloadGameTests.class,
                ResultGameTests.class,
                ScriptProgressGameTests.class,
                SimplificationGameTests.class,
                SpiritCommandGameTests.class,
                SpiritScriptsGameTests.class);
    }
}
