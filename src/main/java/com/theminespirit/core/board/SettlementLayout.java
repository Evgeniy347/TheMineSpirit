package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

/**
 * Раскладка участка поселения этапа 1 (решение 54b п.2): детерминированно от якоря — дома поселения, он же центр
 * участка. Участок — квадрат {@link #PLOT}×{@link #PLOT} (участок дома семьи 23×23 — решение владельца по поселению),
 * по периметру — ограда {@code fence} с калиткой в середине северной стороны; хижина {@code hut_1} — в центре
 * дверью к калитке; вход в шахту — внутри ограды, в стороне от хижины с отступом не меньше 3;
 * рабочий двор (верстак, сундуки, печь) — по другую сторону хижины; факелы освещения — перед хижиной и за ней,
 * у калитки и по углам ограды — факелы на её столбах из схемы ограды. Ничто не пересекается — сторож {@code SettlementLayoutTests}.
 */
public final class SettlementLayout {
    public static final int PLOT = 23;
    public static final int HALF = PLOT / 2;
    // Выравнивание захватывает полосу за линией ограды: уступ земли у забора давал перелезть через него (INC-53).
    public static final int LEVEL_MARGIN = 2;
    public static final int LEVEL_HALF = HALF + LEVEL_MARGIN;
    // Хижина 5×5 и ряд крыльца перед дверью (схема hut_1: 5 в ширину, 6 в глубину с крыльцом).
    public static final int HOUSE_WIDTH = 5;
    public static final int HOUSE_DEPTH = 6;
    public static final int MINE_OFFSET = 6;
    public static final int YARD_OFFSET = -6;
    public static final int LIGHT_X = 3;
    public static final int LIGHT_NORTH = -6;
    public static final int LIGHT_SOUTH = 5;

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

    // Колонка спуска в шахту: ступени 3 в ширину вглубь на юг от входа до края участка. Её высоту выравнивание не
    // трогает — иначе подсыпка засыпала бы спуск, а ровность участка не достигалась бы никогда (INC-50).
    public static boolean mineColumn(BlockCoord anchor, int x, int z) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится вход в шахту");
        BlockCoord entrance = mineEntrance(anchor);
        return Math.abs(x - entrance.x()) <= 1 && z >= entrance.z() - 1 && z <= anchor.z() + LEVEL_HALF;
    }

    // Рабочий двор: здесь дух ставит верстак, сундуки и печь — вне габарита дома.
    public static BlockCoord workYard(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится рабочий двор");
        return new BlockCoord(anchor.x() + YARD_OFFSET, anchor.y(), anchor.z());
    }

    // Факелы освещения на уровне ног — перед хижиной и за ней (±3 по x), в стороне от двора станций и спуска в
    // шахту (у калитки и по углам ограды светят факелы на её столбах). Углы участка не годились — там деревья до
    // расчистки; углы хижины ±4 — «занята» станцией двора и «вне досягаемости» у спуска (приёмка «джунгли»).
    public static List<BlockCoord> lighting(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставятся факелы");
        return List.of(
                new BlockCoord(anchor.x() - LIGHT_X, anchor.y(), anchor.z() + LIGHT_NORTH),
                new BlockCoord(anchor.x() + LIGHT_X, anchor.y(), anchor.z() + LIGHT_NORTH),
                new BlockCoord(anchor.x() - LIGHT_X, anchor.y(), anchor.z() + LIGHT_SOUTH),
                new BlockCoord(anchor.x() + LIGHT_X, anchor.y(), anchor.z() + LIGHT_SOUTH));
    }
}
