package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PickupSpotTests {
    private static final BlockCoord ITEM = new BlockCoord(0, 1, 0);

    @Test
    void accessCell_ItemUnderBush_ReturnsBushCell() {
        MapSafety safety = plantedBed();

        assertEquals(new BlockCoord(0, 1, 0), PickupSpot.accessCell(new BlockCoord(0, 0, 0), safety),
                "Ягода падает сквозь куст на землю: клетка доступа — сам куст над предметом, иначе подбор пойдёт "
                        + "к пашне под кустом, куда не встать");
    }

    @Test
    void accessCell_ItemOnOpenGround_ReturnsItemCell() {
        MapSafety safety = new MapSafety().floor();

        assertEquals(ITEM, PickupSpot.accessCell(ITEM, safety),
                "Предмет на открытом месте доступен из своей клетки: поведение обычного дропа не меняется");
    }

    @Test
    void forDrop_ItemInsideHazardBush_ReturnsAdjacentStand() {
        MapSafety safety = plantedBed();

        Optional<BlockCoord> spot = PickupSpot.forDrop(ITEM, new BlockCoord(3, 1, 0), safety);

        assertEquals(Optional.of(new BlockCoord(2, 1, 0)), spot,
                "Ягоды выпадают в клетку куста (HAZARD), а дух не заходит в куст: подбор обязан встать на ближайшую "
                        + "безопасную клетку в двух шагах от духа — с неё куст достаётся, иначе сбор падает");
    }

    @Test
    void forDrop_EveryStandWithinRadiusIsHazard_ReturnsEmpty() {
        MapSafety safety = new MapSafety().floor();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx != 0 || dz != 0) {
                    safety.set(dx, 1, dz, Cell.HAZARD);
                }
            }
        }

        Optional<BlockCoord> spot = PickupSpot.forDrop(ITEM, new BlockCoord(3, 1, 0), safety);

        assertEquals(Optional.empty(), spot,
                "Если все клетки вокруг куста — тоже кусты, стоять негде: подбор не выдумывает стоянку в опасной "
                        + "клетке, и вызывающий обрабатывает недостижимость как раньше");
    }

    @Test
    void forDrop_BedFieldItemCenter_ReturnsWestStand() {
        MapSafety safety = new MapSafety().floor();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                safety.set(x, 1, z, Cell.HAZARD);
            }
        }

        Optional<BlockCoord> spot = PickupSpot.forDrop(new BlockCoord(4, 1, 4), new BlockCoord(1, 1, 3), safety);

        assertEquals(Optional.of(new BlockCoord(2, 1, 3)), spot,
                "В центре грядки 3x3 стоянка обязана быть ближайшей безопасной клеткой у духа, а не на другой стороне "
                        + "грядки: иначе дух идёт через кусты, получает урон и бросает подбор");
    }

    @Test
    void forDrop_ItemInWater_ReturnsEmpty() {
        MapSafety safety = new MapSafety().floor();

        Optional<BlockCoord> spot = PickupSpot.forDrop(ITEM, new BlockCoord(3, 1, 0),
                safety.set(0, 1, 0, Cell.WATER));

        assertEquals(Optional.empty(), spot,
                "В воду за предметом дух не лезет и рядом с водой не встаёт: стоянка не находится, "
                        + "и вызывающий сохраняет прежнее поведение");
    }

    @Test
    void forDrop_ItemInLava_ReturnsEmpty() {
        MapSafety safety = new MapSafety().floor();

        Optional<BlockCoord> spot = PickupSpot.forDrop(ITEM, new BlockCoord(3, 1, 0),
                safety.set(0, 1, 0, Cell.LAVA));

        assertEquals(Optional.empty(), spot,
                "Возле лавы стоять нельзя: подбор не имеет права предлагать стоянку у лавовой клетки");
    }

    @Test
    void forDrop_WaterBesideStand_StillReturnsStand() {
        MapSafety safety = plantedBed();
        safety.set(-3, 0, -1, Cell.WATER);

        Optional<BlockCoord> spot = PickupSpot.forDrop(ITEM, new BlockCoord(3, 1, 0), safety);

        assertEquals(Optional.of(new BlockCoord(2, 1, 0)), spot,
                "Вода у пола рядом со стоянкой не запрещает подбор (дух стоит на твёрдом полу у воды, как на ферме): "
                        + "запрет отправлял духа в обход грядки, где он застревал");
    }

    @Test
    void forDrop_ItemOnOpenGround_ReturnsItemCell() {
        MapSafety safety = new MapSafety().floor();

        Optional<BlockCoord> spot = PickupSpot.forDrop(ITEM, new BlockCoord(3, 1, 0), safety);

        assertEquals(Optional.of(ITEM), spot,
                "Предмет на проходимой клетке подбирается с неё же: поведение обычного дропа не меняется");
    }

    @Test
    void forDrop_BushSurroundedByLava_ReturnsEmpty() {
        MapSafety safety = new MapSafety().floor();
        safety.set(0, 1, 0, Cell.HAZARD);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    safety.set(dx, 1, dz, Cell.LAVA);
                }
            }
        }

        Optional<BlockCoord> spot = PickupSpot.forDrop(ITEM, new BlockCoord(3, 1, 0), safety);

        assertTrue(spot.isEmpty(),
                "Куст в лавовом кольце: безопасной стоянки рядом нет, подбор не должен подставлять духа под лаву");
    }

    private static MapSafety plantedBed() {
        MapSafety safety = new MapSafety().floor();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                safety.set(dx, 1, dz, Cell.HAZARD);
            }
        }
        return safety;
    }

    private static final class MapSafety implements SafetyView {
        private final Map<BlockCoord, Cell> cells = new HashMap<>();

        MapSafety floor() {
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    cells.put(new BlockCoord(x, 0, z), Cell.SOLID);
                }
            }
            return this;
        }

        MapSafety set(int x, int y, int z, Cell cell) {
            cells.put(new BlockCoord(x, y, z), cell);
            return this;
        }

        @Override
        public Cell cellAt(BlockCoord coord) {
            return cells.getOrDefault(coord, Cell.PASSABLE);
        }
    }
}
