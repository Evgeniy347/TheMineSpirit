package com.theminespirit.core.intellect;

import com.theminespirit.core.inventory.InventoryFill;
import java.util.List;
import java.util.Objects;

public final class FreeInventoryDemand {
    private FreeInventoryDemand() {}

    public static double demand(InventoryFill fill, InventoryNormTable table) {
        Objects.requireNonNull(fill, "Заполнение инвентаря не может быть null: по нему считают спрос");
        return demand(fill.occupiedFraction(), fill.extraFraction(), table);
    }

    public static double demand(double occupiedFraction, double extraFraction, InventoryNormTable table) {
        Objects.requireNonNull(table, "Таблица нормы не может быть null: кривые спроса — данные");
        checkFraction(occupiedFraction, "Доля занятых ячеек");
        checkFraction(extraFraction, "Доля лишнего");
        double value =
                interpolate(table.occupiedCurve(), occupiedFraction) + interpolate(table.extraCurve(), extraFraction);
        return Math.min(IntellectTuning.loadDefault().maxDemand(), value);
    }

    static double interpolate(List<InventoryNormTable.CurvePoint> curve, double at) {
        if (at <= curve.get(0).at()) {
            return curve.get(0).value();
        }
        for (int index = 1; index < curve.size(); index++) {
            InventoryNormTable.CurvePoint previous = curve.get(index - 1);
            InventoryNormTable.CurvePoint current = curve.get(index);
            if (at <= current.at()) {
                double span = current.at() - previous.at();
                double part = span == 0 ? 0 : (at - previous.at()) / span;
                return previous.value() + (current.value() - previous.value()) * part;
            }
        }
        return curve.get(curve.size() - 1).value();
    }

    private static void checkFraction(double value, String what) {
        if (Double.isNaN(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(
                    what + " = " + value + ": доля обязана лежать в 0..1, иначе кривую не с чем сравнивать");
        }
    }
}
