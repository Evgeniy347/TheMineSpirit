package com.theminespirit.core.planner;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;

final class FuelBalance {
    private static final int COOK_TICKS = 200;
    private static final Map<String, Integer> BURN_TICKS = Map.of(
            "coal", 1600,
            "charcoal", 1600,
            "oak_log", 300,
            "oak_planks", 300);

    private final long smelts;
    private final long cook;
    private final long burn;

    private FuelBalance(long smelts, long cook, long burn) {
        this.smelts = smelts;
        this.cook = cook;
        this.burn = burn;
    }

    static FuelBalance of(TaskNode plan) {
        Accumulator accumulator = new Accumulator();
        walk(plan, accumulator);
        return new FuelBalance(accumulator.smelts, accumulator.cook, accumulator.burn);
    }

    long smelts() {
        return smelts;
    }

    long cook() {
        return cook;
    }

    long burn() {
        return burn;
    }

    private static void walk(TaskNode node, Accumulator accumulator) {
        if (node instanceof SequenceNode sequence) {
            for (TaskNode step : sequence.steps()) {
                walk(step, accumulator);
            }
            return;
        }
        if (node instanceof ScriptCall call) {
            if (call.script().equals("smelt")) {
                int count = number(call);
                accumulator.smelts += count;
                accumulator.cook += count * (long) COOK_TICKS;
                return;
            }
            if (call.script().equals("gather")) {
                int burn = BURN_TICKS.getOrDefault(String.valueOf(call.params().get("block")), 0);
                accumulator.burn += number(call) * (long) burn;
            }
            return;
        }
        throw new IllegalStateException("FuelBalance не знает узел " + node.getClass()
                + ": молчаливый пропуск шага сделает свойство «топлива хватает» слепым");
    }

    private static int number(ScriptCall call) {
        Object value = call.params().get("count");
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Шаг " + call + " без числового count: свойство не может посчитать баланс");
        }
        return number.intValue();
    }

    private static final class Accumulator {
        private long smelts;
        private long cook;
        private long burn;
    }
}
