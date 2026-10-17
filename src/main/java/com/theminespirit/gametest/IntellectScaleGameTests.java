package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import com.theminespirit.neoforge.intellect.SettlementIntellect;
import com.theminespirit.neoforge.intellect.StockReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntellectScaleGameTests {
    private static final int SPIRITS = 20;
    private static final int OBSERVED_TICKS = 1150;

    private IntellectScaleGameTests() {
    }

    // [флак] замер 1150 тиков наблюдения: окно отката 200 и фаза пересчёта 100 видны только на дистанции
    @GameTest(template = "arena", batch = "intellect_scale", timeoutTicks = 1400)
    public static void scale_TwentySpiritsNoWork_StartsAndFailuresBounded(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        long started = helper.getLevel().getGameTime();
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 5; column++) {
                spirits.add(TacticsScene.spirit(helper, new BlockPos(2 + column, 1, 2 + row)));
            }
        }
        helper.assertTrue(spirits.size() == SPIRITS,
                "В тесте на масштаб " + SPIRITS + " духов: иначе предел ничего не доказывает");
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ());
        spirits.forEach(spirit -> spirit.setHome(home));
        Map<String, Long> failTick = new HashMap<>();
        Map<String, Long> lastGen = new HashMap<>();

        helper.succeedWhen(() -> {
            SettlementKey key = SettlementGroups.keyOf(spirits.get(0).home(), spirits.get(0).getUUID());
            SpiritCoordinator coordinator = CoordinatorRegistry.get(helper.getLevel(), key);
            SettlementIntellect intellect = coordinator.intellect();
            long now = helper.getLevel().getGameTime();
            for (SpiritEntity spirit : spirits) {
                String id = spirit.getUUID().toString();
                long gen = spirit.taskGeneration();
                Long prev = lastGen.put(id, gen);
                boolean restarted = prev != null && gen != prev;
                boolean empty = spirit.currentTaskScript().isEmpty();
                boolean failed = spirit.taskStatus().map(status -> status == RunStatus.FAILED)
                        .orElse(false);
                if (empty && failed && !failTick.containsKey(id)) {
                    failTick.put(id, now);
                }
                if (restarted && failTick.containsKey(id) && startedScript(spirit)) {
                    helper.assertTrue(now - failTick.get(id) >= IntellectTuning.loadDefault().failBackoffTicks(),
                            "Дух " + id + " получил работу через " + (now - failTick.get(id))
                                    + " тиков после провала вместо 200: откат не держит");
                    failTick.remove(id);
                }
            }
            long elapsed = now - started;
            if (elapsed >= OBSERVED_TICKS) {
                helper.assertTrue(intellect.decideCalls() >= 2,
                        "Интеллект пересчитал спрос дважды: иначе бюджеты делить не на что");
                helper.assertTrue(intellect.decideCalls() * IntellectTuning.loadDefault().demandIntervalTicks()
                                <= elapsed + IntellectTuning.loadDefault().demandIntervalTicks(),
                        "Пересчётов не больше одного на 100 тиков: иначе снимок собирается каждый тик");
                helper.assertTrue(intellect.entityQueries() <= intellect.decideCalls(),
                        "Запросов сущностей не больше числа пересчётов: один сбор врагов на решение");
                helper.assertTrue(StockReader.STOCK_READ_BUDGET == 4096,
                        "Бюджет чтений запасов 4096: число в тесте, факт — из production");
                helper.assertTrue(intellect.stockReads() <= StockReader.STOCK_READ_BUDGET
                                * intellect.decideCalls(),
                        "Чтений запасов не больше бюджета на число пересборок: иначе тик читает полмира");
                helper.assertTrue(IntellectTuning.loadDefault().failBackoffTicks() == 200,
                        "Откат провала 200 тиков: число в тесте, факт — из production");
                helper.assertTrue(intellect.taskFailures() >= SPIRITS,
                        "Провалов не меньше числа духов: иначе механизм отката вообще не включался");
                helper.assertTrue(intellect.taskFailures() <= SPIRITS * (OBSERVED_TICKS
                                / IntellectTuning.loadDefault().failBackoffTicks()),
                        "Провалов не больше пяти окон отката на духа: иначе задачи перезапускаются без отката");
                helper.assertTrue(intellect.taskStarts() <= SPIRITS
                                * (1 + OBSERVED_TICKS / IntellectTuning.loadDefault().demandIntervalTicks()),
                        "Назначений не больше одного на духа за период спроса: иначе дёргаем задачи каждый тик");
                helper.assertTrue(intellect.view().orElseThrow().population() == SPIRITS,
                        "В снимке все " + SPIRITS + " духов: иначе решение делят без части поселения");
                spirits.forEach(SpiritEntity::discard);
                TacticsScene.finish(helper, spirits.get(0));
            }
        });
    }

    private static boolean startedScript(SpiritEntity spirit) {
        return !spirit.currentTaskScript().orElse("").isEmpty();
    }

    // [флак] замер ~410 тиков: провал, окно отката 200 и фаза пересчёта 100
    @GameTest(template = "arena", batch = "intellect_backoff", timeoutTicks = 800)
    public static void backoff_FailedWork_NotRestartedWithinWindow(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        spirit.setHome(new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ()));
        String id = spirit.getUUID().toString();
        boolean[] failed = {false};
        long[] failTick = {-1};
        long[] lastGen = {spirit.taskGeneration()};

        helper.succeedWhen(() -> {
            SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());
            SpiritCoordinator coordinator = CoordinatorRegistry.get(helper.getLevel(), key);
            SettlementIntellect intellect = coordinator.intellect();
            helper.assertTrue(intellect.decideCalls() >= 1,
                    "Интеллект поселения ещё не решил: ждём первый пересчёт спроса");
            long now = helper.getLevel().getGameTime();
            long gen = spirit.taskGeneration();
            boolean restarted = gen != lastGen[0];
            lastGen[0] = gen;
            boolean empty = spirit.currentTaskScript().isEmpty();
            boolean isFailed = spirit.taskStatus().map(status -> status == RunStatus.FAILED)
                    .orElse(false);
            if (!failed[0]) {
                if (empty && isFailed) {
                    failed[0] = true;
                    failTick[0] = now;
                }
            } else if (restarted && startedScript(spirit)) {
                helper.assertTrue(now - failTick[0] >= IntellectTuning.loadDefault().failBackoffTicks(),
                        "Работа после провала перезапущена через " + (now - failTick[0])
                                + " тиков вместо 200: откат не держит");
                spirit.discard();
                TacticsScene.finish(helper, spirit);
            }
        });
    }

    @GameTest(template = "arena", batch = "intellect_no_farm", timeoutTicks = 200)
    public static void noFarm_HungryWithoutCrops_DoesNotAssignFarm(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        spirit.setHome(new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ()));

        helper.succeedWhen(() -> {
            SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());
            SpiritCoordinator coordinator = CoordinatorRegistry.get(helper.getLevel(), key);
            SettlementIntellect intellect = coordinator.intellect();
            helper.assertTrue(intellect.decision().isPresent(),
                    "Интеллект ещё не решил: ждём первый пересчёт спроса");
            helper.assertTrue(intellect.view().orElseThrow().farmWork().matureCrops() == 0,
                    "В арене нет зрелых посевов: снимок работы фермы обязан это показать");
            boolean farmAssigned = intellect.decision().orElseThrow().activities().assignments().stream()
                    .anyMatch(assignment -> assignment.activity() == ActivityId.FARM);
            helper.assertFalse(farmAssigned,
                    "Голодное поселение без зрелых посевов не берёт ферму: иначе harvest_crop падает "
                            + "«зрелых посевов в радиусе 8 нет»");
            helper.assertFalse("harvest_crop".equals(spirit.currentTaskScript().orElse("")),
                    "Дух без грядок не запускает harvest_crop: иначе задача падает на пустой ферме");
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }
}
