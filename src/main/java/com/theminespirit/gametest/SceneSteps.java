package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import org.slf4j.Logger;

/**
 * Укрупнённая сцена механики (1–3 сцены на механику): шаги — методы {@link SceneStep} классов механики, идут подряд
 * на одной площадке. Тело шага получает {@link StepHelper}: его {@code succeed}/{@code succeedWhen} завершают шаг, а
 * не сцену. Шаг не прошёл за свой бюджет — причина с именем шага запоминается, сцена идёт дальше и в конце падает
 * со списком упавших шагов. Между шагами площадка чистится узко, доска, координатор и мобы возвращаются к умолчанию
 * GameTest-сервера.
 *
 * <p>Тайм-аут сцены не меньше суммы бюджетов шагов (+1 тик на переход), это проверяется при запуске.
 */
final class SceneSteps {
    private static final int CLEAR_HEIGHT = 24;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final GameTestHelper helper;
    private final List<Step> steps;
    private int index = -1;
    private int stepTick;
    private final List<String> failures = new ArrayList<>();
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
        SceneSteps scene = new SceneSteps(helper, steps);
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

    // Упавший шаг не прячет остальные: сцена идёт дальше и падает в конце со списком всех упавших шагов.
    private void failStep(Step step, String reason) {
        String failure = "шаг «" + step.name() + "»: " + reason;
        LOGGER.error("укрупнённая сцена: {}", failure);
        failures.add(failure);
        finishCurrent();
        startNext();
    }

    private void finishCurrent() {
        current.end();
        GameTestFilter.setBoardEnabled(false);
        GameTestFilter.setCoordinatorEnabled(false);
        GameTestFilter.setMobsTargetSpirits(true);
        GameTestFixtures.clearStage(helper, CLEAR_HEIGHT);
    }

    private void startNext() {
        index++;
        stepTick = 0;
        if (index >= steps.size()) {
            GameTestFixtures.finish(helper, CLEAR_HEIGHT);
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
