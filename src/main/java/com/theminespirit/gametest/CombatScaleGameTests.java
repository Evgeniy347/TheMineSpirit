package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CombatScaleGameTests {
    private CombatScaleGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void scale_TwentySpiritsAttackOneHusk_QueriesStayWithinLimits(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 5; column++) {
                spirits.add(TacticsScene.spirit(helper, new BlockPos(column + 1, 1, row + 1)));
            }
        }
        helper.assertTrue(spirits.size() == 20, "в тесте на масштаб 20 духов, создано " + spirits.size());
        TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(9, 1, 9));

        for (SpiritEntity spirit : spirits) {
            spirit.startTask(new ScriptCall("attack", Map.of()));
        }

        int observedTicks = 200;
        int evalInterval = 10;
        int maxEntityQueries = 20 * observedTicks / evalInterval + 20 * observedTicks;
        int maxBlockReads = 20 * observedTicks / evalInterval * 64 + 20 * observedTicks * 8;
        int maxSteps = 20 * observedTicks;

        helper.succeedWhen(() -> {
            int reads = 0;
            int queries = 0;
            int steps = 0;
            for (SpiritEntity spirit : spirits) {
                helper.assertTrue(
                        spirit.tickCount >= observedTicks,
                        "дух ещё не отработал " + observedTicks + " тиков: " + spirit.tickCount);
                reads += spirit.tactics().blockReads();
                queries += spirit.tactics().entityQueries();
                steps += spirit.tactics().stepCalls();
            }
            helper.assertTrue(
                    steps > 0 && reads > 0 && queries > 0,
                    "тактика обязана работать, иначе предел ничего не доказывает: шагов " + steps + ", чтений "
                            + reads);
            helper.assertTrue(
                    queries <= maxEntityQueries,
                    "запросов сущностей не больше " + maxEntityQueries + ", было " + queries);
            helper.assertTrue(reads <= maxBlockReads, "чтений блоков не больше " + maxBlockReads + ", было " + reads);
            helper.assertTrue(steps <= maxSteps, "шагов тактики не больше " + maxSteps + ", было " + steps);
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }
}
