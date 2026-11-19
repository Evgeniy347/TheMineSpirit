package com.theminespirit.core.town;

import com.theminespirit.core.building.api.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

final class StageSiteChecks {
    private StageSiteChecks() {}

    record Pieces(Placement hut, Placement yard, Placement mine, MinePlot minePlot, Placement orchard) {}

    record Plots(
            List<BlockCoord> orchardSaplings,
            List<OrchardPlot> extraOrchards,
            List<Placement> fields,
            List<Placement> placements) {}

    record Masks(
            ColumnMask level,
            ColumnMask zone,
            ColumnMask streets,
            ColumnMask enclosure,
            ColumnMask fenceStrip,
            FenceLine ring) {}

    static void check(BlockCoord anchor, Pieces pieces, String wood, Plots plots, Masks masks) {
        Objects.requireNonNull(anchor, "Якорь этапа не может быть null: от него высота площадок");
        Objects.requireNonNull(pieces.hut(), "Место хижины не может быть null: этап 1 без хижины не собрать");
        Objects.requireNonNull(pieces.yard(), "Место двора не может быть null");
        Objects.requireNonNull(pieces.mine(), "Место спуска в шахту не может быть null");
        Objects.requireNonNull(
                pieces.minePlot(),
                "Прямоугольник шахты не может быть null: обе вкладки карты и стройка "
                        + "берут спуск из плана участка");
        Objects.requireNonNull(pieces.orchard(), "Место сада не может быть null");
        WoodSpecies.check(wood);
        Objects.requireNonNull(
                plots.orchardSaplings(), "Места деревьев сада не могут быть null: пусто — пустой список");
        Objects.requireNonNull(plots.extraOrchards(), "Дополнительные сады не могут быть null: пусто — пустой список");
        Objects.requireNonNull(plots.fields(), "Поля этапа не могут быть null");
        Objects.requireNonNull(plots.placements(), "Постройки этапа не могут быть null: пусто — пустой список");
        Objects.requireNonNull(masks.level(), "Площадки выравнивания не могут быть null");
        Objects.requireNonNull(
                masks.zone(), "Зона поселения не может быть null: по ней идут аудит, выравнивание и защита");
        Objects.requireNonNull(masks.streets(), "Улицы плана не могут быть null: по ним дух не берёт материалы плана");
        Objects.requireNonNull(masks.enclosure(), "Огороженная область не может быть null");
        Objects.requireNonNull(masks.fenceStrip(), "Полоса ограды не может быть null: по ней сносится старая ограда");
        Objects.requireNonNull(masks.ring(), "Ограда этапа не может быть null");
    }
}
