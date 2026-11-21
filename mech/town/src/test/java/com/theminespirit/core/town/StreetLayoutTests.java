package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreetLayoutTests {
    private static final PresetLibrary PRESETS = DataTables.INSTANCE.presets().current();
    private static final StreetLayout STREETS = StreetLayout.of(PRESETS);
    private static final int SIZE = 5;

    @Test
    void lay_StraightNorthSouth_StraightTilesAlongAxis() {
        ColumnMask street = ColumnMask.rect(0, 0, 4, 19);
        List<StreetTile> tiles = STREETS.lay(street, 0, 0, SIZE);
        assertExact(street, tiles);
        assertTrue(
                tiles.stream()
                        .allMatch(tile -> "street5_straight".equals(tile.scheme())
                                && tile.rotation().quarterTurns() % 2 == 0),
                "прямая улица север–юг — прямые плитки без поворота поперёк: " + tiles
                        + " Иначе тест lay_StraightNorthSouth_StraightTilesAlongAxis перестанет ловить эту поломку. При смене lay_StraightNorthSouth_StraightTilesAlongAxis проверь это утверждение.");
    }

    @Test
    void lay_RightAngle_TurnTileInCorner() {
        ColumnMask street = ColumnMask.rect(0, 0, 4, 14).union(ColumnMask.rect(0, 10, 14, 14));
        List<StreetTile> tiles = STREETS.lay(street, 0, 0, SIZE);
        assertExact(street, tiles);
        assertEquals(
                "street5_turn",
                at(tiles, 0, 10).scheme(),
                "угол улицы — плитка поворота: " + tiles
                        + " Иначе тест lay_RightAngle_TurnTileInCorner перестанет ловить эту поломку. При смене lay_RightAngle_TurnTileInCorner проверь это утверждение.");
    }

    @Test
    void lay_TeeJunction_TeeTileAtJoin() {
        ColumnMask street = ColumnMask.rect(0, 0, 14, 4).union(ColumnMask.rect(5, 0, 9, 14));
        List<StreetTile> tiles = STREETS.lay(street, 0, 0, SIZE);
        assertExact(street, tiles);
        assertEquals(
                "street5_t",
                at(tiles, 5, 0).scheme(),
                "примыкание улицы — Т-образная плитка: " + tiles
                        + " Иначе тест lay_TeeJunction_TeeTileAtJoin перестанет ловить эту поломку. При смене lay_TeeJunction_TeeTileAtJoin проверь это утверждение.");
    }

    @Test
    void lay_Crossroads_CrossTileInCentre() {
        ColumnMask street = ColumnMask.rect(0, 5, 14, 9).union(ColumnMask.rect(5, 0, 9, 14));
        List<StreetTile> tiles = STREETS.lay(street, 0, 0, SIZE);
        assertExact(street, tiles);
        assertEquals(
                "street5_cross",
                at(tiles, 5, 5).scheme(),
                "перекрёсток — плитка «+»: " + tiles
                        + " Иначе тест lay_Crossroads_CrossTileInCentre перестанет ловить эту поломку. При смене lay_Crossroads_CrossTileInCentre проверь это утверждение.");
    }

    @Test
    void lay_RaggedEdge_PatchesCloseWithoutOverlap() {
        ColumnMask street = ColumnMask.rect(0, 0, 4, 9).union(ColumnMask.rect(5, 3, 6, 4));
        List<StreetTile> tiles = STREETS.lay(street, 0, 0, SIZE);
        assertExact(street, tiles);
        assertEquals(
                4,
                tiles.stream()
                        .filter(tile -> StreetLayout.PATCH.equals(tile.scheme()))
                        .count(),
                "край улицы, куда полная плитка не ложится, добивается заплатками 1×1: " + tiles
                        + " Иначе тест lay_RaggedEdge_PatchesCloseWithoutOverlap перестанет ловить эту поломку. При смене lay_RaggedEdge_PatchesCloseWithoutOverlap проверь это утверждение.");
    }

    private static StreetTile at(List<StreetTile> tiles, int x, int z) {
        return tiles.stream()
                .filter(tile -> tile.x() == x && tile.z() == z)
                .findFirst()
                .orElseThrow(() -> new AssertionError("нет плитки в (" + x + ", " + z + "): " + tiles));
    }

    private static void assertExact(ColumnMask street, List<StreetTile> tiles) {
        Map<ColumnMask.Cell, Integer> covered = new HashMap<>();
        for (StreetTile tile : tiles) {
            Schematic schematic = PRESETS.byId(tile.scheme()).orElseThrow().schematic();
            for (Schematic.PlacedEntry entry :
                    schematic.entries(new BlockCoord(tile.x(), 0, tile.z()), tile.rotation())) {
                covered.merge(new ColumnMask.Cell(entry.pos().x(), entry.pos().z()), 1, Integer::sum);
            }
        }
        assertEquals(
                Set.copyOf(street.cells()),
                covered.keySet(),
                "плитки закрывают ровно улицу: дыра — нераспаханная трава, лишняя клетка — тропа в чужом участке"
                        + " Иначе тест assertExact перестанет ловить эту поломку. При смене assertExact проверь это утверждение.");
        assertTrue(
                covered.values().stream().allMatch(count -> count == 1),
                "плитки улицы стыкуются без наложений: " + covered
                        + " Иначе тест assertExact перестанет ловить эту поломку. При смене assertExact проверь это утверждение.");
    }
}
