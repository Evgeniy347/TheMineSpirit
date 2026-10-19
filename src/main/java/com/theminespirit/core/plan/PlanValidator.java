package com.theminespirit.core.plan;

import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ConditionSpec;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ParamType;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PlanValidator {
    private PlanValidator() {}

    public static TaskNode validate(TaskNode root, ScriptCatalog scripts, ConditionCatalog conditions) {
        Objects.requireNonNull(root, "План не может быть null: валидировать нечего");
        Objects.requireNonNull(scripts, "Каталог скриптов не может быть null: без него не проверить имена и параметры");
        Objects.requireNonNull(conditions, "Каталог проверок не может быть null: без него не проверить условия");
        return validateNode(root, scripts, conditions);
    }

    private static TaskNode validateNode(TaskNode node, ScriptCatalog scripts, ConditionCatalog conditions) {
        return switch (node) {
            case ScriptCall call -> validateScript(call, scripts);
            case SequenceNode sequence -> new SequenceNode(validateSteps(sequence.steps(), scripts, conditions));
            case FallbackNode fallback -> new FallbackNode(validateSteps(fallback.steps(), scripts, conditions));
            case ParallelNode parallel ->
                new ParallelNode(parallel.mode(), validateSteps(parallel.steps(), scripts, conditions));
            case RepeatNode repeat -> validateRepeat(repeat, scripts, conditions);
            case ConditionNode condition -> validateCondition(condition, scripts, conditions);
        };
    }

    private static List<TaskNode> validateSteps(
            List<TaskNode> steps, ScriptCatalog scripts, ConditionCatalog conditions) {
        List<TaskNode> validated = new ArrayList<>(steps.size());
        for (TaskNode step : steps) {
            validated.add(validateNode(step, scripts, conditions));
        }
        return validated;
    }

    private static TaskNode validateRepeat(RepeatNode repeat, ScriptCatalog scripts, ConditionCatalog conditions) {
        int times = repeat.times();
        if (times < CatalogLimits.REPEAT_TIMES_MIN || times > CatalogLimits.REPEAT_TIMES_MAX) {
            throw new PlanValidationException("У узла repeat times=" + times + " вне диапазона ["
                    + CatalogLimits.REPEAT_TIMES_MIN + ", " + CatalogLimits.REPEAT_TIMES_MAX
                    + "]: повтор на огромное число итераций превращает план в бесконечный цикл, "
                    + "сверьте лимиты в AGENTS.md");
        }
        return new RepeatNode(times, validateNode(repeat.step(), scripts, conditions));
    }

    private static TaskNode validateScript(ScriptCall call, ScriptCatalog scripts) {
        ScriptSpec spec = scripts.find(call.script())
                .orElseThrow(() -> new PlanValidationException(
                        "Неизвестный скрипт '" + call.script() + "': выберите скрипт из каталога /v1/capabilities"));
        return new ScriptCall(
                call.script(), validateParams("скрипта '" + call.script() + "'", call.params(), spec.params()));
    }

    private static TaskNode validateCondition(
            ConditionNode condition, ScriptCatalog scripts, ConditionCatalog conditions) {
        ConditionSpec spec = conditions
                .find(condition.check())
                .orElseThrow(() -> new PlanValidationException("Неизвестная проверка '" + condition.check()
                        + "': проверка не зарегистрирована в каталоге условий"));
        Map<String, Object> params =
                validateParams("проверки '" + condition.check() + "'", condition.params(), spec.params());
        TaskNode thenStep = validateNode(condition.thenStep(), scripts, conditions);
        TaskNode elseStep =
                condition.elseStep() == null ? null : validateNode(condition.elseStep(), scripts, conditions);
        return new ConditionNode(condition.check(), params, thenStep, elseStep);
    }

    private static Map<String, Object> validateParams(
            String owner, Map<String, Object> provided, List<ParamSpec> schema) {
        Map<String, ParamSpec> byName = new LinkedHashMap<>();
        for (ParamSpec param : schema) {
            byName.put(param.name(), param);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : provided.entrySet()) {
            ParamSpec param = byName.get(entry.getKey());
            if (param == null) {
                throw new PlanValidationException("Неизвестный параметр '" + entry.getKey() + "' у " + owner
                        + ": сверьте имя с каталогом /v1/capabilities");
            }
            result.put(entry.getKey(), normalize(owner, param, entry.getValue()));
        }
        for (ParamSpec param : schema) {
            if (param.required() && !result.containsKey(param.name())) {
                throw new PlanValidationException(
                        "У " + owner + " не задан обязательный параметр '" + param.name() + "' (" + param.type() + ")");
            }
        }
        for (ParamSpec param : schema) {
            if (!param.required() && param.defaultValue() != null && !result.containsKey(param.name())) {
                result.put(param.name(), normalize(owner, param, param.defaultValue()));
            }
        }
        return result;
    }

    private static Object normalize(String owner, ParamSpec param, Object value) {
        if (value == null) {
            throw new PlanValidationException(
                    "Параметр '" + param.name() + "' у " + owner + " равен null: null-значение недопустимо в плане");
        }
        return switch (param.type()) {
            case INT -> normalizeInt(owner, param, value);
            case DOUBLE -> normalizeDouble(owner, param, value);
            case STRING -> normalizeString(owner, param, value);
            case STRING_LIST -> normalizeStringList(owner, param, value);
            case BOOL -> normalizeBool(owner, param, value);
            case ENUM -> normalizeEnum(owner, param, value);
        };
    }

    private static Integer normalizeInt(String owner, ParamSpec param, Object value) {
        if (value instanceof Integer integer) {
            checkRange(owner, param, (double) integer, integer);
            return integer;
        }
        if (value instanceof Long longValue) {
            if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
                throw new PlanValidationException(
                        "Параметр '" + param.name() + "' у " + owner + " равен " + longValue
                                + " и не помещается в int: тип INT допускает только 32-битные целые, сверьте план с /v1/capabilities");
            }
            int integer = longValue.intValue();
            checkRange(owner, param, (double) integer, integer);
            return integer;
        }
        throw typeError(owner, param, "целое число", value);
    }

    private static Double normalizeDouble(String owner, ParamSpec param, Object value) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            checkRange(owner, param, d, value);
            return d;
        }
        throw typeError(owner, param, "число", value);
    }

    private static String normalizeString(String owner, ParamSpec param, Object value) {
        if (!(value instanceof String text)) {
            throw typeError(owner, param, "строку", value);
        }
        return text;
    }

    private static List<String> normalizeStringList(String owner, ParamSpec param, Object value) {
        if (!(value instanceof List<?> list)) {
            throw typeError(owner, param, "массив строк", value);
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof String text)) {
                throw typeError(owner, param, "массив строк", value);
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static Boolean normalizeBool(String owner, ParamSpec param, Object value) {
        if (!(value instanceof Boolean booleanValue)) {
            throw typeError(owner, param, "true или false", value);
        }
        return booleanValue;
    }

    private static String normalizeEnum(String owner, ParamSpec param, Object value) {
        if (!(value instanceof String text)) {
            throw typeError(owner, param, "строку с допустимым значением", value);
        }
        if (param.values() == null || !param.values().contains(text)) {
            throw enumError(owner, param, value);
        }
        return text;
    }

    private static void checkRange(String owner, ParamSpec param, double value, Object actual) {
        if (param.min() != null && value < param.min()) {
            throw rangeError(owner, param, actual);
        }
        if (param.max() != null && value > param.max()) {
            throw rangeError(owner, param, actual);
        }
    }

    private static PlanValidationException rangeError(String owner, ParamSpec param, Object actual) {
        return new PlanValidationException("Параметр '" + param.name() + "' у " + owner + " равен " + actual
                + " и выходит за диапазон " + bounds(param) + ": сверьте значение с /v1/capabilities");
    }

    private static String bounds(ParamSpec param) {
        String minStr = param.min() == null ? "-∞" : formatBound(param.type(), param.min());
        String maxStr = param.max() == null ? "+∞" : formatBound(param.type(), param.max());
        return "[" + minStr + ", " + maxStr + "]";
    }

    private static String formatBound(ParamType type, double value) {
        if (type == ParamType.INT) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static PlanValidationException enumError(String owner, ParamSpec param, Object actual) {
        return new PlanValidationException("Параметр '" + param.name() + "' у " + owner + " равен " + actual
                + ": допустимы " + param.values() + ", сверьте значение с /v1/capabilities");
    }

    private static PlanValidationException typeError(String owner, ParamSpec param, String expected, Object actual) {
        return new PlanValidationException("Параметр '" + param.name() + "' у " + owner + " ожидает " + expected
                + ", а получено " + describe(actual) + ": сверьте тип со схемой параметров в /v1/capabilities");
    }

    private static String describe(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        return value.getClass().getSimpleName() + " (" + value + ")";
    }
}
