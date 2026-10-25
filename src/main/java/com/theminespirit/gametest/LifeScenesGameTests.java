package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «жизнь духа и сохранение»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeScenesGameTests {
    private LifeScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_life_arena", timeoutTicks = 5331)
    public static void lifeScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena", "empty"),
                ChunkLoadingGameTests.class,
                ExperienceGameTests.class,
                ModLoadingGameTests.class,
                SpiritDespawnGameTests.class,
                SpiritGameTests.class,
                SpiritTickingGameTests.class,
                TaskPersistenceGameTests.class);
    }
}
