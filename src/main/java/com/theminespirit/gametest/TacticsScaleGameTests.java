package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsScaleGameTests {
    private static final int SPIRITS = 50;
    private static final int OBSERVED_TICKS = 200;
    private static final int MAX_BLOCK_READS = 50 * 200 / 10 * 64;
    private static final int MAX_ENTITY_QUERIES = 50 * 200 / 10;
    private static final int MAX_STEPS = 50 * 200 / 10 + 50;

    private TacticsScaleGameTests() {}

    @GameTest(template = "arena", batch = "tactics_scale", timeoutTicks = 400)
    public static void scale_FiftySpiritsSeeOneHuskForTwoHundredTicks_ReadsQueriesAndStepsStayWithinLimits(
            GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 8 && spirits.size() < SPIRITS; column++) {
                spirits.add(TacticsScene.spirit(helper, new BlockPos(column + 1, 1, row + 1)));
            }
        }
        helper.assertTrue(
                spirits.size() == SPIRITS, "в тесте на масштаб " + SPIRITS + " духов, создано " + spirits.size());
        TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 9));

        helper.succeedWhen(() -> {
            int reads = 0;
            int queries = 0;
            int steps = 0;
            for (SpiritEntity spirit : spirits) {
                helper.assertTrue(
                        spirit.tickCount >= OBSERVED_TICKS,
                        "дух ещё не отработал " + OBSERVED_TICKS + " тиков: " + spirit.tickCount);
                helper.assertTrue(
                        spirit.tactics().failures() == 0,
                        "тактика не падала: " + spirit.tactics().lastFailure().orElse("нет"));
                reads += spirit.tactics().blockReads();
                queries += spirit.tactics().entityQueries();
                steps += spirit.tactics().stepCalls();
            }
            helper.assertTrue(
                    steps > 0 && reads > 0 && queries > 0,
                    "тактика обязана работать, иначе предел ничего не доказывает: оценок " + steps + ", чтений "
                            + reads);
            helper.assertTrue(
                    reads <= MAX_BLOCK_READS,
                    "чтений блоков за " + OBSERVED_TICKS + " тиков не больше " + MAX_BLOCK_READS + ", было " + reads);
            helper.assertTrue(
                    queries <= MAX_ENTITY_QUERIES,
                    "запросов сущностей не больше " + MAX_ENTITY_QUERIES + ", было " + queries);
            helper.assertTrue(
                    steps <= MAX_STEPS, "вызовов TacticsEngine.step не больше " + MAX_STEPS + ", было " + steps);
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }
}
