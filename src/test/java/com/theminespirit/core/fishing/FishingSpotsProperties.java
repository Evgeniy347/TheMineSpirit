package com.theminespirit.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class FishingSpotsProperties {
    private static final FishingData DATA = new FishingData(2, 1);
    private static final int POND_X = 2;
    private static final int POND_Z = 2;
    private static final int SIDE = 5;

    @Property
    void findWorkPoint_AnyOriginNearPond_IsDeterministicAndOnThePond(
            @ForAll @IntRange(min = -40, max = 40) int originX,
            @ForAll @IntRange(min = -40, max = 40) int originZ) {
        WaterView view = openShore();
        BlockCoord origin = new BlockCoord(originX, 64, originZ);

        FishingSpots.Spot first = FishingSpots.findWorkPoint(origin, view, DATA, FishingSpots.SpotFilter.ALL)
                .orElseThrow();
        FishingSpots.Spot second = FishingSpots.findWorkPoint(origin, view, DATA, FishingSpots.SpotFilter.ALL)
                .orElseThrow();

        assertEquals(first, second,
                "Выбор рабочей точки обязан быть детерминирован: два одинаковых запроса дают одно место, "
                        + "иначе дух мечется между берегами");
        assertTrue(first.water().x() >= POND_X && first.water().x() < POND_X + SIDE
                        && first.water().z() >= POND_Z && first.water().z() < POND_Z + SIDE,
                "Цель заброса обязана лежать в пятне воды, а не в воздухе: иначе удочка летит мимо, "
                        + "фактическая клетка " + first.water());
        assertTrue(first.stand().y() == 64 || first.stand().y() == 65,
                "Рабочая стоянка обязана быть на уровне воды или на блок выше: с обрыва дух не забросит, "
                        + "фактическая высота " + first.stand().y());
    }

    @Property
    void findWorkPoint_AnyOriginWithRejectingFilter_Refuses(
            @ForAll @IntRange(min = -40, max = 40) int originX,
            @ForAll @IntRange(min = -40, max = 40) int originZ) {
        WaterView view = openShore();

        assertTrue(FishingSpots.findWorkPoint(new BlockCoord(originX, 64, originZ), view, DATA,
                        (stand, cell) -> false).isEmpty(),
                "Фильтр, отвергающий весь берег, обязан оставить рабочую точку ненайденной: "
                        + "запрет построек не обходится подстановкой места по умолчанию");
    }

    private static WaterView openShore() {
        List<WaterView.Column> columns = new ArrayList<>();
        for (int dx = 0; dx < SIDE; dx++) {
            for (int dz = 0; dz < SIDE; dz++) {
                columns.add(new WaterView.Column(POND_X + dx, POND_Z + dz, 64, 2));
            }
        }
        return new WaterView() {
            @Override
            public Collection<Column> columns() {
                return columns;
            }

            @Override
            public boolean canStand(BlockCoord feet) {
                return feet.y() == 64
                        && (feet.x() < POND_X || feet.x() >= POND_X + SIDE
                                || feet.z() < POND_Z || feet.z() >= POND_Z + SIDE);
            }
        };
    }
}
