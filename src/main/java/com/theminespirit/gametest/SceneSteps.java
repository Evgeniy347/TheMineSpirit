package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

final class SceneSteps {
    private static final int CLEAR_HEIGHT = 24;
    private static final int TICKING_MARGIN = 40;
    private static final int TICKING_SPAN = 16;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final GameTestHelper helper;
    private final List<Step> steps;
    private int index = -1;
    private int stepTick;
    private long stepStartNanos;
    private final List<String> failures = new ArrayList<>();
    private final List<ChunkPos> forced = new ArrayList<>();
    private StepHelper current;

    private SceneSteps(GameTestHelper helper, List<Step> steps) {
        this.helper = helper;
        this.steps = steps;
    }

    static void run(GameTestHelper helper, List<String> templates, Class<?>... owners) {
        runSteps(helper, collect(templates, owners));
    }

    static void runSteps(GameTestHelper helper, List<Step> steps) {
        helper.assertTrue(!steps.isEmpty(), "укрупнённая сцена без шагов ничего не проверяет");
        int total = 0;
        for (Step step : steps) {
            total += step.budgetTicks() + 1;
        }
        int timeout = helper.testInfo.getTimeoutTicks();
        helper.assertTrue(
                timeout >= total,
                "тайм-аут сцены " + timeout + " меньше суммы бюджетов шагов (+1 тик на переход) " + total
                        + ": последние шаги упали бы по чужому тайм-ауту; поставь timeoutTicks = " + total);
        GameTestFixtures.forgetChests(helper.getLevel());
        SceneSteps scene = new SceneSteps(helper, steps);
        scene.forceTickingAround();
        scene.startNext();
        helper.onEachTick(scene::tick);
    }

    static List<Step> collect(List<String> templates, Class<?>... owners) {
        List<Step> steps = new ArrayList<>();
        for (Class<?> owner : owners) {
            List<Method> methods = new ArrayList<>();
            for (Method method : owner.getMethods()) {
                SceneStep step = method.getAnnotation(SceneStep.class);
                if (step != null && Modifier.isStatic(method.getModifiers()) && templates.contains(step.template())) {
                    methods.add(method);
                }
            }
            methods.sort(Comparator.comparing(Method::getName));
            for (Method method : methods) {
                steps.add(new Step(method, method.getAnnotation(SceneStep.class)));
            }
        }
        return steps;
    }

    private void tick() {
        if (index >= steps.size()) {
            return;
        }
        Step step = steps.get(index);
        boolean passed;
        try {
            passed = current.tick();
        } catch (GameTestAssertException failure) {
            failStep(step, failure.getMessage());
            return;
        } catch (RuntimeException failure) {
            LOGGER.error("шаг «{}» упал", step.name(), failure);
            failStep(step, "упал: " + failure);
            return;
        }
        if (passed) {
            finishCurrent();
            startNext();
            return;
        }
        stepTick++;
        if (stepTick > step.budgetTicks()) {
            failStep(step, "не уложился в " + step.budgetTicks() + " тиков: " + current.lastFailure());
        }
    }

    private void failStep(Step step, String reason) {
        String failure = "шаг «" + step.name() + "»: " + reason;
        LOGGER.error("укрупнённая сцена: {}", failure);
        failures.add(failure);
        finishCurrent();
        startNext();
    }

    private void finishCurrent() {
        current.end();
        LOGGER.info(
                "шаг «{}»: {} тиков, {} мс",
                steps.get(index).name(),
                stepTick,
                (System.nanoTime() - stepStartNanos) / 1_000_000);
        GameTestFilter.setBoardEnabled(false);
        GameTestFilter.setCoordinatorEnabled(false);
        GameTestFilter.setMobsTargetSpirits(true);
        GameTestFixtures.clearStage(helper, CLEAR_HEIGHT);
    }

    private void forceTickingAround() {
        ServerLevel level = helper.getLevel();
        BlockPos low = helper.absolutePos(new BlockPos(-TICKING_MARGIN, 0, -TICKING_MARGIN));
        BlockPos high =
                helper.absolutePos(new BlockPos(TICKING_SPAN + TICKING_MARGIN, 0, TICKING_SPAN + TICKING_MARGIN));
        int minX = Math.min(low.getX(), high.getX()) >> 4;
        int maxX = Math.max(low.getX(), high.getX()) >> 4;
        int minZ = Math.min(low.getZ(), high.getZ()) >> 4;
        int maxZ = Math.max(low.getZ(), high.getZ()) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong()) && level.setChunkForced(x, z, true)) {
                    forced.add(chunk);
                }
            }
        }
    }

    private void releaseTicking() {
        ServerLevel level = helper.getLevel();
        for (ChunkPos chunk : forced) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
        forced.clear();
    }

    private void startNext() {
        index++;
        stepTick = 0;
        stepStartNanos = System.nanoTime();
        if (index >= steps.size()) {
            GameTestFixtures.finish(helper, CLEAR_HEIGHT);
            releaseTicking();
            LOGGER.info(
                    "укрупнённая сцена: {} шагов за {} тиков, упало {}",
                    steps.size(),
                    helper.getTick(),
                    failures.size());
            if (failures.isEmpty()) {
                helper.succeed();
            } else {
                helper.fail(
                        "упало шагов " + failures.size() + " из " + steps.size() + ": " + String.join("; ", failures));
            }
            return;
        }
        Step step = steps.get(index);
        current = new StepHelper(helper);
        GameTestFilter.setBoardEnabled(step.spec().board());
        GameTestFilter.setCoordinatorEnabled(step.spec().coordinator());
        GameTestFilter.setMobsTargetSpirits(!step.spec().calmMobs());
        try {
            step.method().invoke(null, current);
        } catch (InvocationTargetException thrown) {
            Throwable cause = thrown.getCause();
            if (!(cause instanceof GameTestAssertException)) {
                LOGGER.error("шаг «{}»: подготовка упала", step.name(), cause);
            }
            failStep(step, "подготовка: " + cause);
        } catch (IllegalAccessException notPublic) {
            failStep(step, "шаг обязан быть public static: " + notPublic);
        }
    }

    record Step(Method method, SceneStep spec) {
        String name() {
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }

        int budgetTicks() {
            return spec.timeoutTicks();
        }
    }
}
