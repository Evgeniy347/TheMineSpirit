package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

/**
 * Раскладка участка поселения этапа 1 (решение 54b п.2): детерминированно от якоря — дома поселения, он же центр
 * участка. Участок — квадрат {@link #PLOT}×{@link #PLOT} (участок дома семьи 23×23 — решение владельца по поселению),
 * по периметру — ограда {@code fence} с калиткой в середине северной стороны; хижина {@code hut_1} — в центре
 * дверью к калитке; вход в шахту — внутри ограды, в стороне от хижины с отступом не меньше 3;
 * рабочий двор (верстак, сундуки, печь) — по другую сторону хижины; факелы освещения — по углам вокруг хижины,
 * у калитки и по углам ограды — факелы на её столбах из схемы ограды. Ничто не пересекается — сторож {@code SettlementLayoutTests}.
 */
public final class SettlementLayout {
    public static final int PLOT = 23;
    public static final int HALF = PLOT / 2;
    // Хижина 5×5 и ряд крыльца перед дверью (схема hut_1: 5 в ширину, 6 в глубину с крыльцом).
    public static final int HOUSE_WIDTH = 5;
    public static final int HOUSE_DEPTH = 6;
    public static final int MINE_OFFSET = 6;
    public static final int YARD_OFFSET = -6;
    public static final int LIGHT_OFFSET = 4;

    private SettlementLayout() {}

    // Угол участка (северо-западный) — начало схемы ограды {@code fence}.
    public static BlockCoord fenceOrigin(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него раскладывается поселение");
        return new BlockCoord(anchor.x() - HALF, anchor.y(), anchor.z() - HALF);
    }

    // Калитка — середина северной стороны ограды.
    public static BlockCoord gate(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится калитка");
        return new BlockCoord(anchor.x(), anchor.y(), anchor.z() - HALF);
    }

    // Начало схемы хижины: корпус 5×5 вокруг якоря, ряд крыльца — севернее, к калитке.
    public static BlockCoord houseOrigin(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится дом");
        return new BlockCoord(anchor.x() - HOUSE_WIDTH / 2, anchor.y(), anchor.z() - HOUSE_DEPTH / 2);
    }

    // Начало спуска в шахту (пол первой ступени — на блок ниже поверхности).
    public static BlockCoord mineEntrance(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится вход в шахту");
        return new BlockCoord(anchor.x() + MINE_OFFSET, anchor.y() - 1, anchor.z());
    }

    // Рабочий двор: здесь дух ставит верстак, сундуки и печь — вне габарита дома.
    public static BlockCoord workYard(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится рабочий двор");
        return new BlockCoord(anchor.x() + YARD_OFFSET, anchor.y(), anchor.z());
    }

    // Факелы освещения на уровне ног — по углам вокруг хижины, с отступом от стен (у калитки и по углам ограды
    // светят факелы на её столбах). Углы участка не годились: там до расчистки деревья, после — «стоянка
    // недостижима» (приёмка «джунгли» 23.10).
    public static List<BlockCoord> lighting(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставятся факелы");
        int inner = LIGHT_OFFSET;
        return List.of(
                new BlockCoord(anchor.x() - inner, anchor.y(), anchor.z() - inner),
                new BlockCoord(anchor.x() + inner, anchor.y(), anchor.z() - inner),
                new BlockCoord(anchor.x() - inner, anchor.y(), anchor.z() + inner),
                new BlockCoord(anchor.x() + inner, anchor.y(), anchor.z() + inner));
    }
}
