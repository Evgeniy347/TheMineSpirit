package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UnprotectAreasTests {
    private static final String DIMENSION = "minecraft:overworld";
    private static final long NOW = 1_000_000L;
    private static BlockProtection protection;

    @BeforeAll
    static void loadProtection() {
        protection = ProtectionConfig.load();
    }

    private static UnprotectArea area(String id, BlockCoord min, BlockCoord max, long expiresAt) {
        return new UnprotectArea(id, DIMENSION, new BlockBox(min, max), "игрок", NOW - 10, "убери постройку", expiresAt);
    }

    private static List<String> buildingNeighbours() {
        List<String> list = new ArrayList<>(Collections.nCopies(14, "minecraft:oak_planks"));
        list.addAll(Collections.nCopies(12, "minecraft:air"));
        return list;
    }

    private static BlockProtection.Decision check(UnprotectAreas areas, BlockCoord pos, long now) {
        boolean inUnprotectArea = areas.covers(DIMENSION, pos, now);
        return protection.check("minecraft:cobblestone", buildingNeighbours(),
                new BlockProtection.Placement(true, false, inUnprotectArea));
    }

    @Test
    void covers_BlockInsideActiveArea_LeavesPlayerBuildingUnprotected() {
        UnprotectAreas areas = new UnprotectAreas();
        areas.add(area("a", new BlockCoord(0, 0, 0), new BlockCoord(2, 2, 2), 0), 4096, NOW);

        assertEquals(new BlockProtection.Allowed(), check(areas, new BlockCoord(1, 1, 1), NOW),
                "Внутри разрешённой области постройка игрока перестаёт считаться чужой: иначе снятие защиты не работает");
    }

    @Test
    void covers_BlockOutsideArea_StillRefusesPlayerBuilding() {
        UnprotectAreas areas = new UnprotectAreas();
        areas.add(area("a", new BlockCoord(0, 0, 0), new BlockCoord(2, 2, 2), 0), 4096, NOW);

        BlockProtection.Refused refused = assertInstanceOf(BlockProtection.Refused.class,
                check(areas, new BlockCoord(3, 1, 1), NOW),
                "Снаружи разрешённой области осторожная защита остаётся: радиус разрешения не расширяется");
        assertEquals(PolicyId.PLAYER_BUILDINGS, refused.policy(),
                "Снаружи области отказ называет постройку игрока, а не разрешённую область");
    }

    @Test
    void covers_ExpiredArea_DoesNotLiftProtection() {
        UnprotectAreas areas = new UnprotectAreas();
        areas.add(area("a", new BlockCoord(0, 0, 0), new BlockCoord(2, 2, 2), NOW + 1_000), 4096, NOW);

        assertFalse(areas.covers(DIMENSION, new BlockCoord(1, 1, 1), NOW + 1_000),
                "Истёкшее разрешение не покрывает блок: срок жизни не продлевается сам");
        assertInstanceOf(BlockProtection.Refused.class, check(areas, new BlockCoord(1, 1, 1), NOW + 1_000),
                "После истечения срока защита возвращается: постройка игрока снова неприкосновенна");
    }

    @Test
    void maxUnprotectVolume_ShippedValue_Is4096() {
        assertEquals(4096, ProtectionConfig.load().maxUnprotectVolume(),
                "Предел объёма разрешённой области берётся из protection.json → maxUnprotectVolume");
    }

    @Test
    void add_AreaBiggerThanLimit_RefusesWithNumbers() {
        int limit = ProtectionConfig.load().maxUnprotectVolume();
        UnprotectAreas areas = new UnprotectAreas();
        UnprotectArea tooBig = area("a", new BlockCoord(0, 0, 0), new BlockCoord(0, limit, 0), 0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> areas.add(tooBig, limit, NOW),
                "Область объёмом больше предела отвергается до сохранения: снятие защиты на весь мир недопустимо");

        assertTrue(error.getMessage().contains("Объём области " + (limit + 1L)) && error.getMessage().contains("больше предела " + limit),
                "Отказ называет фактический объём и предел из данных. Фактическое: " + error.getMessage());
        assertEquals(0, areas.size(), "Отвергнутая область не попадает в список разрешений");
    }

    @Test
    void add_AreaAtTheLimit_IsAccepted() {
        int limit = ProtectionConfig.load().maxUnprotectVolume();
        UnprotectAreas areas = new UnprotectAreas();
        UnprotectArea atLimit = area("a", new BlockCoord(0, 0, 0), new BlockCoord(0, limit - 1, 0), 0);

        areas.add(atLimit, limit, NOW);

        assertTrue(areas.covers(DIMENSION, new BlockCoord(0, limit - 1, 0), NOW),
                "Область ровно в предел принимается: граница предела включительная");
    }
}
