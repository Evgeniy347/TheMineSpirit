package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «политики безопасности»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyScenesGameTests {
    private PolicyScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_policy_arena", timeoutTicks = 9662)
    public static void policyScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                PolicyDangerGameTests.class,
                PolicyHazardGameTests.class,
                PolicyLayerGameTests.class,
                PolicyProtectionGameTests.class,
                PolicySuspendGameTests.class,
                PolicyWaterGameTests.class);
    }
}
