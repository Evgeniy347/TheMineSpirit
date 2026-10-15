package com.theminespirit.core.planner;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.plan.PlanBudget;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * «Добыть предмет любым способом»: владеет вложенным подпланом — инвентарь, ближние сундуки,
 * затем ResourceResolver (gather/craft/smelt). Провал шага перечитывает мир и пересобирает
 * подплан не больше {@link CatalogLimits#ACQUIRE_MAX_ATTEMPTS} раз.
 */
public final class AcquireScript implements ScriptInstance {
    private final String item;
    private final int count;
    private final ResourceKnowledge knowledge;
    private final AcquireSupplies supplies;
    private final ScriptCatalog catalog;
    private final ConditionCatalog conditions;
    private final Supplier<ScriptRegistry> registry;
    private final ConditionRegistry conditionRegistry;

    private TaskRunner runner;
    private int attempts;
    private int startInventory;
    private int chestPlanned;
    private ScriptStatus finalStatus;
    private String failureReason;
    private String lastStepFailure;

    public AcquireScript(String item, int count, ResourceKnowledge knowledge, AcquireSupplies supplies,
            ScriptCatalog catalog, ConditionCatalog conditions, Supplier<ScriptRegistry> registry,
            ConditionRegistry conditionRegistry) {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Предмет для acquire пуст: без id скрипт не знает, что добыть");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Число предметов " + count + " не положительно: цель добычи "
                    + "должна быть хотя бы 1");
        }
        this.item = item;
        this.count = count;
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge не может быть null: без знаний резолвер "
                + "не отличит крафт от сбора");
        this.supplies = Objects.requireNonNull(supplies, "supplies не может быть null: без снабжения скрипт "
                + "не прочитает инвентарь и сундуки");
        this.catalog = Objects.requireNonNull(catalog, "catalog не может быть null: без каталога подплан "
                + "не пройдёт валидацию");
        this.conditions = Objects.requireNonNull(conditions, "conditions не может быть null: без каталога "
                + "проверок подплан не пройдёт валидацию");
        this.registry = Objects.requireNonNull(registry, "registry не может быть null: реестр скриптов отдаётся "
                + "лениво, иначе регистрация acquire упирается в саму себя");
        this.conditionRegistry = Objects.requireNonNull(conditionRegistry, "conditionRegistry не может быть null: "
                + "без реестра проверок вложенный раннер не выберет ветку");
    }

    @Override
    public ScriptStatus tick() {
        if (finalStatus != null) {
            return finalStatus;
        }
        if (runner == null) {
            if (attempts >= CatalogLimits.ACQUIRE_MAX_ATTEMPTS) {
                return fail("лимит попыток исчерпан");
            }
            Optional<TaskNode> plan;
            try {
                plan = buildSubplan();
            } catch (RuntimeException error) {
                return fail(error.getMessage());
            }
            if (plan.isEmpty()) {
                finalStatus = ScriptStatus.SUCCEEDED;
                return ScriptStatus.SUCCEEDED;
            }
            runner = TaskRunner.start(plan.get(), registry.get(), conditionRegistry);
        }
        RunStatus status = runner.tick();
        switch (status) {
            case RUNNING -> {
                return ScriptStatus.RUNNING;
            }
            case SUCCEEDED -> {
                finalStatus = ScriptStatus.SUCCEEDED;
                return ScriptStatus.SUCCEEDED;
            }
            case CANCELLED -> {
                return fail("подплан был отменён");
            }
            case FAILED -> {
                lastStepFailure = runner.failureReason().orElse("причина шага не указана");
                if (attempts < CatalogLimits.ACQUIRE_MAX_ATTEMPTS) {
                    // Пересборка на следующем тике: один перечит мира на провал шага,
                    // не чаще одного внутреннего тика на внешний.
                    runner = null;
                    return ScriptStatus.RUNNING;
                }
                return fail(lastStepFailure);
            }
        }
        return ScriptStatus.RUNNING;
    }

    /**
     * Собирает подплан: инвентарь → сундуки → остаток через резолвер. Пустой результат —
     * цель уже лежит в инвентаре, мир трогать не нужно. Пакетный доступ — чтобы тесты
     * осматривали дерево, не запуская вложенный раннер.
     */
    Optional<TaskNode> buildSubplan() {
        attempts++;
        startInventory = supplies.inInventory(item);
        if (startInventory >= count) {
            chestPlanned = 0;
            return Optional.empty();
        }
        List<ScriptCall> withdraws = supplies.withdraw(item, count - startInventory);
        chestPlanned = withdraws.stream().mapToInt(step -> count(step)).sum();
        int need = count - startInventory - chestPlanned;
        List<TaskNode> nodes = new ArrayList<>(withdraws);
        if (need > 0) {
            nodes.add(ResourceResolver.resolve(item, need, knowledge, catalog, conditions,
                    supplies.inventory(), supplies.nearbyStations()));
        }
        TaskNode plan = new SequenceNode(nodes);
        plan = PlanValidator.validate(plan, catalog, conditions);
        PlanBudget.Budget budget = PlanBudget.measure(plan);
        if (budget.exceeds()) {
            throw new PlanValidationException("Подплан acquire '" + item + "' ×" + count + " выходит за бюджет: "
                    + budget.describe() + ": разбейте цель на меньшие задачи");
        }
        return Optional.of(plan);
    }

    private static int count(ScriptCall step) {
        Object value = step.params().get("count");
        return value instanceof Integer integer ? integer : 0;
    }

    private ScriptStatus fail(String detail) {
        finalStatus = ScriptStatus.FAILED;
        int now = supplies.inInventory(item);
        int gathered = Math.max(0, now - startInventory);
        failureReason = "не удалось добыть '" + item + "' ×" + count
                + ": в инвентаре при старте " + startInventory
                + ", из сундуков запланировано " + chestPlanned
                + ", сейчас в инвентаре " + now
                + ", пополнено " + gathered
                + ", попыток " + attempts
                + (detail == null || detail.isBlank() ? "" : "; причина: " + detail);
        return ScriptStatus.FAILED;
    }

    @Override
    public Optional<Progress> progress() {
        if (runner == null) {
            return Optional.empty();
        }
        return runner.progress().map(step -> step.done() != null && step.target() != null
                ? new Progress(step.done(), step.target())
                : new Progress(step.step(), step.totalSteps()));
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public PolicyExemptions policyExemptions() {
        return runner == null ? PolicyExemptions.empty() : runner.policyExemptions();
    }

    @Override
    public void suspend() {
        if (runner != null) {
            runner.suspend();
        }
    }

    @Override
    public void resume() {
        if (runner != null) {
            runner.resume();
        }
    }

    @Override
    public void cancel() {
        if (runner != null) {
            runner.cancel();
        }
    }

    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("attempts", attempts);
        if (lastStepFailure != null) {
            state.put("lastStepFailure", lastStepFailure);
        }
        if (runner != null) {
            runner.snapshot().ifPresent(snapshot -> state.put("runner", TaskSnapshotCodec.encode(snapshot)));
        }
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        Objects.requireNonNull(state, "state не может быть null: снимок скрипта приходит картой");
        Object savedAttempts = state.get("attempts");
        if (savedAttempts instanceof Integer integer) {
            attempts = integer;
        }
        Object failure = state.get("lastStepFailure");
        if (failure instanceof String text) {
            lastStepFailure = text;
        }
        Object snapshot = state.get("runner");
        if (snapshot instanceof String encoded) {
            runner = TaskRunner.restore(TaskSnapshotCodec.decode(encoded), registry.get(), conditionRegistry);
        } else {
            runner = null;
        }
    }

    int attempts() {
        return attempts;
    }
}
