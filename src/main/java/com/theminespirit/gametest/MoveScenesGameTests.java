package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «движение»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoveScenesGameTests {
    private MoveScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_move_arena", timeoutTicks = 16195)
    public static void moveScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                DescentGameTests.class,
                DoorsGameTests.class,
                ExploreGameTests.class,
                FollowGameTests.class,
                MovementApproachGameTests.class,
                MovementCarefulGameTests.class,
                MovementClimbGameTests.class,
                MovementComeGameTests.class,
                MovementDistanceGameTests.class,
                MovementGotoGameTests.class,
                MovementHomeGameTests.class,
                MovementPatrolGameTests.class,
                MovementStepGameTests.class,
                MovementWanderGameTests.class,
                NightLeashGameTests.class,
                PersonalSpaceGameTests.class,
                PillarGameTests.class,
                WallEscapeGameTests.class,
                WaterCrossGameTests.class);
    }

    @GameTest(template = "big_arena", batch = "scene_move_big_arena", timeoutTicks = 19229)
    public static void moveScene_BigArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("big_arena"), HouseMovementGameTests.class);
    }
}
