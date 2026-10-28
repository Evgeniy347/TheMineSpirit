package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsScenesGameTests {
    private TacticsScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_tactics_arena", timeoutTicks = 21319)
    public static void tacticsScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                CombatDisengageGameTests.class,
                CombatDirectiveGameTests.class,
                CombatTargetsGameTests.class,
                CombatAttackGameTests.class,
                CombatFleeGameTests.class,
                CombatScaleGameTests.class,
                CombatRallyGameTests.class,
                DefenseGameTests.class,
                DefenseScaleGameTests.class,
                EscortGameTests.class,
                MobTargetsGameTests.class,
                TacticsCalmGameTests.class,
                TacticsFactsGameTests.class,
                TacticsJournalGameTests.class,
                TacticsLeashGameTests.class,
                TacticsModesGameTests.class,
                TacticsPersistenceGameTests.class,
                TacticsPolicyGameTests.class,
                TacticsRetreatGameTests.class,
                TacticsScaleGameTests.class,
                TacticsWitchGameTests.class);
    }
}
