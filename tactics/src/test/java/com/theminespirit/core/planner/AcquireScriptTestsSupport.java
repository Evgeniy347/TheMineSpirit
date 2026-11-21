package com.theminespirit.core.planner;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.ScriptCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class AcquireScriptTestsSupport {
    static final String ITEM = "stick";
    static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();
    static final ConditionRegistry NO_CONDITIONS = ConditionRegistry.of(Map.of());

    static Supplier<ScriptRegistry> countingRegistry(AtomicInteger calls, int ticks) {
        return () -> {
            calls.incrementAndGet();
            return ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(ticks, new AtomicInteger())));
        };
    }

    static ScriptStatus run(AcquireScript script, int maxTicks) {
        ScriptStatus status = ScriptStatus.RUNNING;
        for (int i = 0; i < maxTicks && status == ScriptStatus.RUNNING; i++) {
            status = script.tick();
        }
        return status;
    }

    static RunStatus runToCompletion(TaskRunner runner) {
        RunStatus status = RunStatus.RUNNING;
        for (int i = 0; i < 20 && status == RunStatus.RUNNING; i++) {
            status = runner.tick();
        }
        return status;
    }

    static AcquireScript script(
            int count, StubSupplies supplies, LocalStubKnowledge knowledge, AtomicInteger registryCalls) {
        return new AcquireScript(
                ITEM,
                count,
                knowledge,
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(countingRegistry(registryCalls, 10), NO_CONDITIONS));
    }

    static LocalStubKnowledge alwaysGatherable() {
        return new LocalStubKnowledge();
    }

    static List<ScriptCall> withdrawSteps(int steps) {
        List<ScriptCall> calls = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            calls.add(new ScriptCall(
                    "withdraw",
                    Map.of(
                            "container",
                            i + ",64," + i,
                            "item",
                            ITEM,
                            "count",
                            1,
                            "radius",
                            CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
        }
        return calls;
    }

    static final class StubSupplies implements AcquireSupplies {
        final int inventory;
        final Map<String, Integer> chests;
        List<ScriptCall> chestSteps;
        Map<String, Integer> inventoryOverride = Map.of();
        Set<String> stations = Set.of();
        AtomicInteger inventoryLive;

        StubSupplies(int inventory, Map<String, Integer> chests) {
            this.inventory = inventory;
            this.chests = new LinkedHashMap<>(chests);
        }

        @Override
        public int inInventory(String item) {
            return inventoryLive == null ? inventory : inventoryLive.get();
        }

        @Override
        public Map<String, Integer> inventory() {
            return inventoryOverride;
        }

        @Override
        public Set<String> nearbyStations() {
            return stations;
        }

        @Override
        public List<ScriptCall> withdraw(String item, int maxCount) {
            if (chestSteps != null) {
                return chestSteps;
            }
            List<ScriptCall> steps = new ArrayList<>();
            int left = maxCount;
            for (Map.Entry<String, Integer> chest : chests.entrySet()) {
                int available = Math.min(chest.getValue(), left);
                if (available <= 0) {
                    continue;
                }
                steps.add(new ScriptCall(
                        "withdraw",
                        Map.of(
                                "container",
                                chest.getKey(),
                                "item",
                                item,
                                "count",
                                available,
                                "radius",
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
                left -= available;
            }
            return steps;
        }
    }

    static List<String> scriptsOf(TaskNode node) {
        List<String> scripts = new ArrayList<>();
        collect(node, scripts);
        return scripts;
    }

    static void collect(TaskNode node, List<String> scripts) {
        switch (node) {
            case ScriptCall call ->
                scripts.add(call.script() + ":"
                        + call.params().getOrDefault("block", call.params().getOrDefault("item", "?")));
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, scripts));
            default ->
                throw new IllegalArgumentException(
                        "Узел " + node.getClass().getSimpleName() + " не разбирается: тест ждёт sequence и script");
        }
    }

    static final class FuelAwareKnowledge implements ResourceKnowledge {
        @Override
        public boolean isGatherable(String item) {
            return "raw_iron".equals(item) || "coal".equals(item) || "cobblestone".equals(item);
        }

        @Override
        public boolean isCraftable(String item) {
            return "furnace".equals(item);
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of(new Ingredient("cobblestone", 8));
        }

        @Override
        public boolean isSmeltable(String item) {
            return "iron_ingot".equals(item);
        }

        @Override
        public String smeltInputFor(String item) {
            return "raw_iron";
        }

        @Override
        public Optional<String> stationFor(String item) {
            return "iron_ingot".equals(item) ? Optional.of("furnace") : Optional.empty();
        }

        @Override
        public int burnTicks(String item) {
            return "coal".equals(item) ? 1600 : 0;
        }
    }

    static final class AlwaysFailingInstance implements ScriptInstance {
        @Override
        public ScriptStatus tick() {
            return ScriptStatus.FAILED;
        }

        @Override
        public String failureReason() {
            return "нет блока в радиусе";
        }
    }

    static final class GatherThenFailInstance implements ScriptInstance {
        final AtomicInteger shared;
        boolean gathered;

        GatherThenFailInstance(AtomicInteger sharedTicks) {
            this.shared = sharedTicks;
        }

        @Override
        public ScriptStatus tick() {
            if (!gathered) {
                gathered = true;
                shared.incrementAndGet();
                return ScriptStatus.RUNNING;
            }
            return ScriptStatus.FAILED;
        }

        @Override
        public String failureReason() {
            return "блок пропал после частичной добычи";
        }
    }

    static final class CountingInstance implements ScriptInstance {
        final int target;
        final AtomicInteger shared;
        int done;

        CountingInstance(int target, AtomicInteger sharedTicks) {
            this.target = target;
            this.shared = sharedTicks;
        }

        @Override
        public ScriptStatus tick() {
            done++;
            shared.incrementAndGet();
            return done >= target ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
        }

        @Override
        public Optional<Progress> progress() {
            return Optional.of(new Progress(Math.min(done, target), target));
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("done", done);
        }

        @Override
        public void restoreState(Map<String, Object> state) {
            done = (Integer) state.getOrDefault("done", 0);
        }
    }

    static final class RecordingInstance implements ScriptInstance {
        final List<String> calls;

        RecordingInstance(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ScriptStatus tick() {
            return ScriptStatus.RUNNING;
        }

        @Override
        public void suspend() {
            calls.add("suspend");
        }

        @Override
        public void resume() {
            calls.add("resume");
        }

        @Override
        public void cancel() {
            calls.add("cancel");
        }
    }

    static final class LocalStubKnowledge implements ResourceKnowledge {
        final List<String> gatherQueries = new ArrayList<>();

        @Override
        public boolean isGatherable(String item) {
            gatherQueries.add(item);
            return true;
        }

        @Override
        public boolean isCraftable(String item) {
            return false;
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public boolean isSmeltable(String item) {
            return false;
        }

        @Override
        public String smeltInputFor(String item) {
            throw new AssertionError("Скрипт в тесте не плавит: резолвер не должен спрашивать вход плавки");
        }
    }
}
