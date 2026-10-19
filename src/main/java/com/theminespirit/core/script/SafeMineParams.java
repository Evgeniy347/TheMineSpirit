package com.theminespirit.core.script;

import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.DescentPlan;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.mining.SafeMineMode;
import java.util.Map;
import java.util.Objects;

/**
 * Параметры скрипта {@code safe_mine}: форма шахты, направление, цель лесенки или длина штольни
 * и интервал факелов из данных.
 */
public record SafeMineParams(SafeMineMode mode, DescentDirection direction, int y, int length, int torchInterval) {

    public SafeMineParams {
        Objects.requireNonNull(mode, "Форма шахты не может быть null: без неё неизвестно, копать вниз или вбок");
        Objects.requireNonNull(direction, "Направление шахты не может быть null: без него неизвестно, куда копать");
        if (y < DescentPlan.WORLD_MIN_Y || y > DescentPlan.WORLD_MAX_Y) {
            throw new IllegalArgumentException("Целевой этаж " + y + " вне мира [" + DescentPlan.WORLD_MIN_Y + ", "
                    + DescentPlan.WORLD_MAX_Y + "]: под дном мира копать некуда, а выше потолка сверлиться некуда");
        }
        if (length < CatalogLimits.MINE_LENGTH_MIN || length > CatalogLimits.MINE_LENGTH_MAX) {
            throw new IllegalArgumentException("Длина штольни " + length + " вне диапазона ["
                    + CatalogLimits.MINE_LENGTH_MIN + ", " + CatalogLimits.MINE_LENGTH_MAX
                    + "]: короче некуда копать, длиннее — за один запуск дух не осилит");
        }
        if (torchInterval < MiningTuning.DESCENT_TORCH_INTERVAL_MIN
                || torchInterval > MiningTuning.DESCENT_TORCH_INTERVAL_MAX) {
            throw new IllegalArgumentException("Интервал факелов " + torchInterval + " вне диапазона ["
                    + MiningTuning.DESCENT_TORCH_INTERVAL_MIN + ", " + MiningTuning.DESCENT_TORCH_INTERVAL_MAX
                    + "]: редкий интервал оставит шахту тёмной, частый разорит запас факелов");
        }
    }

    public static SafeMineParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new SafeMineParams(
                SafeMineMode.parse(ScriptArgs.requireString(params, "mode")),
                DescentDirection.parse(ScriptArgs.requireString(params, "direction")),
                ScriptArgs.optionalInt(params, "y")
                        .orElse(DescentData.loadDefault().targetY()),
                ScriptArgs.optionalInt(params, "length").orElse(CatalogLimits.MINE_LENGTH_DEFAULT),
                ScriptArgs.optionalInt(params, "torchInterval")
                        .orElse(DescentData.loadDefault().torchInterval()));
    }
}
