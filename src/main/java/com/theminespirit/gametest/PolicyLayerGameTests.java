package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.policy.PolicyVerdict;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyLayerGameTests {
    private static final int WALK_BEFORE_LAVA = 15;
    private static final int SPIRITS = 20;
    private static final int OBSERVED_TICKS = 100;

    private PolicyLayerGameTests() {
    }

    // [флак] слои и приостановка: сценарий в сотни тиков духа, запас под нагрузкой
    @GameTest(template = "arena", batch = "policy_layers", timeoutTicks = 500)
    public static void layers_LavaAppearsBesideTheWalkingSpirit_TaskSuspendedThenResumedAfterTenQuietTicks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 4));
        BlockPos target = helper.absolutePos(new BlockPos(8, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("goto", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ())), 1);
        boolean[] lavaPlaced = {false};
        boolean[] everSuspended = {false};
        int[] lastReflexTick = {-1};
        int[] resumedAt = {-1};
        boolean[] wasSuspended = {false};
        String[] reason = {null};
        helper.onEachTick(() -> {
            SpiritBrain brain = spirit.brainIfPresent().orElse(null);
            if (brain == null || run.spiritTicks() < 0) {
                return;
            }
            if (!lavaPlaced[0] && run.spiritTicks() >= WALK_BEFORE_LAVA) {
                BlockPos ahead = BlockPos.containing(helper.relativeVec(spirit.position())).offset(1, -1, 0);
                helper.setBlock(ahead, Blocks.LAVA);
                lavaPlaced[0] = true;
            }
            if (spirit.policies().verdict() instanceof PolicyVerdict.Reflex) {
                lastReflexTick[0] = spirit.tickCount;
            }
            boolean suspended = brain.isSuspended();
            if (suspended) {
                everSuspended[0] = true;
                reason[0] = brain.suspendReason().orElse(null);
                helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "приостановленная политикой задача остаётся RUNNING, статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
                helper.assertTrue("goto".equals(spirit.currentTaskScript().orElse(null)),
                        "приостановленная задача сохраняет текущий скрипт goto, а не отменяется");
            }
            if (wasSuspended[0] && !suspended && resumedAt[0] < 0) {
                resumedAt[0] = spirit.tickCount;
            }
            wasSuspended[0] = suspended;
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(everSuspended[0], "лава рядом с идущим духом обязана приостановить задачу");
            helper.assertTrue(reason[0] != null && reason[0].startsWith("LAVA_FIRE"), "причина приостановки называет политику LAVA_FIRE: «" + reason[0] + "»");
            helper.assertTrue(resumedAt[0] - lastReflexTick[0] == 10,
                    "задача возобновляется ровно через 10 безопасных тиков после последнего рефлекса: рефлекс " + lastReflexTick[0] + ", возобновление " + resumedAt[0]);
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "после возобновления goto обязан дойти в обход лавы, статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет")
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_load", timeoutTicks = 300)
    public static void load_TwentyIdleSpirits_ReadsAndQueriesStayWithinTierBudgets(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 5; column++) {
                spirits.add(helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(column * 2, 1, 1 + row * 2)));
            }
        }
        helper.assertTrue(spirits.size() == SPIRITS, "в нагрузочном тесте должно быть 20 духов, создано " + spirits.size());
        Vec3[] starts = spirits.stream().map(SpiritEntity::position).toArray(Vec3[]::new);

        helper.succeedWhen(() -> {
            int blockReads = 0;
            int queries = 0;
            for (int i = 0; i < spirits.size(); i++) {
                SpiritEntity spirit = spirits.get(i);
                helper.assertTrue(spirit.tickCount >= OBSERVED_TICKS, "дух " + i + " ещё не отработал 100 тиков: " + spirit.tickCount);
                helper.assertTrue(spirit.position().distanceTo(starts[i]) < 0.3, "дух " + i + " обязан стоять на месте");
                helper.assertTrue(spirit.policies().blockReads() == 27 * spirit.policies().tierOneRuns(),
                        "ярус 0 не читает блоки: все чтения духа " + i + " идут сеансами яруса 1 по 27 клеток, чтений " + spirit.policies().blockReads()
                                + " при сеансах " + spirit.policies().tierOneRuns());
                blockReads += spirit.policies().blockReads();
                queries += spirit.policies().entityQueries();
            }
            helper.assertTrue(blockReads <= 20 * 27 * (100 / 10 + 1), "чтений блоков за 100 тиков не больше 20 × 27 × 11 = 5940, было " + blockReads);
            helper.assertTrue(queries <= 20 * (100 / 10 + 1), "запросов сущностей за 100 тиков не больше 20 × 11 = 220, было " + queries);
            helper.assertTrue(queries > 0 && blockReads > 0, "датчики обязаны работать, иначе предел ничего не доказывает: чтений " + blockReads + ", запросов " + queries);
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }
}
