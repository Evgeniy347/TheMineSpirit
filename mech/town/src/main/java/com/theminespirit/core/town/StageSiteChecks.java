package com.theminespirit.core.town;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

final class StageSiteChecks {
    private StageSiteChecks() {}

    static void check(
            BlockCoord anchor,
            Placement hut,
            Placement yard,
            Placement mine,
            MinePlot minePlot,
            Placement orchard,
            String wood,
            List<BlockCoord> orchardSaplings,
            List<OrchardPlot> extraOrchards,
            List<Placement> fields,
            List<Placement> placements,
            ColumnMask level,
            ColumnMask zone,
            ColumnMask streets,
            ColumnMask enclosure,
            ColumnMask fenceStrip,
            FenceLine ring) {
        Objects.requireNonNull(anchor, "Якорь этапа не может быть null: от него высота площадок");
        Objects.requireNonNull(hut, "Место хижины не может быть null: этап 1 без хижины не собрать");
        Objects.requireNonNull(yard, "Место двора не может быть null");
        Objects.requireNonNull(mine, "Место спуска в шахту не может быть null");
        Objects.requireNonNull(
                minePlot,
                "Прямоугольник шахты не может быть null: обе вкладки карты и стройка "
                        + "берут спуск из плана участка");
        Objects.requireNonNull(orchard, "Место сада не может быть null");
        WoodSpecies.check(wood);
        Objects.requireNonNull(orchardSaplings, "Места деревьев сада не могут быть null: пусто — пустой список");
        Objects.requireNonNull(extraOrchards, "Дополнительные сады не могут быть null: пусто — пустой список");
        Objects.requireNonNull(fields, "Поля этапа не могут быть null");
        Objects.requireNonNull(placements, "Постройки этапа не могут быть null: пусто — пустой список");
        Objects.requireNonNull(level, "Площадки выравнивания не могут быть null");
        Objects.requireNonNull(zone, "Зона поселения не может быть null: по ней идут аудит, выравнивание и защита");
        Objects.requireNonNull(streets, "Улицы плана не могут быть null: по ним дух не берёт материалы плана");
        Objects.requireNonNull(enclosure, "Огороженная область не может быть null");
        Objects.requireNonNull(fenceStrip, "Полоса ограды не может быть null: по ней сносится старая ограда");
        Objects.requireNonNull(ring, "Ограда этапа не может быть null");
    }
}
