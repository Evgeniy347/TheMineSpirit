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
    // Глубже — пещера под поселением, не место добычи (INC-69).
    public static final int CAVE_DEPTH = 3;
    // Клетки построек плана: двор станций вокруг его точки, высота построек над уровнем участка.
    public static final int YARD_RADIUS = 2;
    public static final int BUILDING_HEIGHT = 7;
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
        // Схема (ширина 3: стенка, проход, стенка) от угла входа идёт по +x: проход на x+1, стенки на x и x+2. Колонка
        // по центру входа (x−1…x+1) оставляла стенку x+2 бугром на ровном участке, и выравнивание срезало её по кругу
        // (сцена 56).
        return x >= entrance.x() && x <= entrance.x() + 2 && z >= entrance.z() - 1 && z <= anchor.z() + LEVEL_HALF;
    }

    // Добыча (gather земли, камня) не берёт блоки участка и пещер под поселением (INC-69: 4-й раз тот же карман под
    // участком). Участок — его площадь с полосой выравнивания на любой высоте; пещера — глубже CAVE_DEPTH под
    // поверхностью колонки в двух участках от центра. Спуск в шахту плана — исключение.
    public static boolean gatherForbidden(BlockCoord anchor, BlockCoord target, int depthBelowSurface) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него считается запретная зона добычи");
        Objects.requireNonNull(target, "Цель добычи не может быть null: проверяется её клетка");
        if (mineColumn(anchor, target.x(), target.z())) {
            return false;
        }
        int dx = Math.abs(target.x() - anchor.x());
        int dz = Math.abs(target.z() - anchor.z());
        if (dx <= LEVEL_HALF && dz <= LEVEL_HALF) {
            return true;
        }
        return depthBelowSurface > CAVE_DEPTH && dx <= 2 * PLOT && dz <= 2 * PLOT;
    }

    // Клетка постройки плана (ограда, хижина, двор, факелы, шахта): расчистка и выравнивание её не трогают, если там
    // стоит поставленное поселением — запрет по плану, а не по порядку шагов (переоткрытая расчистка сносила бы
    // ограду).

    public static boolean planCell(BlockCoord anchor, int x, int y, int z) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: клетки плана считаются от него");
        if (mineColumn(anchor, x, z)) {
            return true;
        }
        if (y < anchor.y() - 1 || y > anchor.y() + BUILDING_HEIGHT) {
            return false;
        }
        int dx = x - anchor.x();
        int dz = z - anchor.z();
        boolean fence =
                (Math.abs(dx) == HALF && Math.abs(dz) <= HALF) || (Math.abs(dz) == HALF && Math.abs(dx) <= HALF);
        boolean hut = houseColumn(anchor, x, z);
        BlockCoord yard = workYard(anchor);
        boolean yardCell = Math.abs(x - yard.x()) <= YARD_RADIUS && Math.abs(z - yard.z()) <= YARD_RADIUS;
        boolean torch = lighting(anchor).stream().anyMatch(cell -> cell.x() == x && cell.z() == z);
        return fence || hut || yardCell || torch;
    }

    // Стоянка подсыпки (INC-72): на участке поселения (кроме спуска в шахту) — только с небом над ней, пещера под
    // участком не цель; вне участка правило не действует.
    public static boolean fillStandAllowed(BlockCoord anchor, BlockCoord stand, boolean sky) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него считается участок");
        Objects.requireNonNull(stand, "Стоянка не может быть null: проверяется её клетка");
        boolean onPlot = Math.abs(stand.x() - anchor.x()) <= LEVEL_HALF
                && Math.abs(stand.z() - anchor.z()) <= LEVEL_HALF
                && !mineColumn(anchor, stand.x(), stand.z());
        return sky || !onPlot;
    }

    // Рабочий двор: здесь дух ставит верстак, сундуки и печь — вне габарита дома.
    // Колонка габарита хижины (с крыльцом).
    public static boolean houseColumn(BlockCoord anchor, int x, int z) {
        BlockCoord house = houseOrigin(anchor);
        return x >= house.x() && x < house.x() + HOUSE_WIDTH && z >= house.z() && z < house.z() + HOUSE_DEPTH;
    }

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
