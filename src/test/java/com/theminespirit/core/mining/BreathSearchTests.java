package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.StringTokenizer;
import org.junit.jupiter.api.Test;

class BreathSearchTests {
    private static CellWorld floodedShaft() {
        CellWorld world = new CellWorld();
        for (int y = -1; y <= 8; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.set(x, y, z, Cell.SOLID);
                }
            }
        }
        for (int y = 0; y <= 5; y++) {
            world.set(0, y, 0, Cell.WATER);
        }
        for (int y = 6; y <= 8; y++) {
            world.set(0, y, 0, Cell.PASSABLE);
        }
        return world;
    }

    @Test
    void nearest_FloodedShaft_FindsCellWithHeadAboveWater() {
        assertEquals(
                Optional.of(new BlockCoord(0, 5, 0)),
                BreathSearch.nearest(floodedShaft(), new BlockCoord(0, 0, 0)),
                "из дна затопленной шахты аварийный подъём ведёт вверх до клетки, где голова над водой; прыжок "
                        + "на месте не спасает (INC-19)");
    }

    @Test
    void nearest_SealedWater_ReturnsEmpty() {
        CellWorld world = floodedShaft();
        world.set(0, 6, 0, Cell.SOLID);

        assertEquals(
                Optional.empty(),
                BreathSearch.nearest(world, new BlockCoord(0, 0, 0)),
                "запечатанная вода без воздуха — подниматься некуда, поиск называет это пустотой, а не клеткой");
    }

    @Test
    void nearest_PoolWithShore_LeadsToDryLandNotWaterSurface() {
        CellWorld world = new CellWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.set(x, 0, z, Cell.WATER);
                world.set(x, 1, z, Cell.WATER);
                world.set(x, -1, z, Cell.SOLID);
            }
        }
        world.set(2, -1, 0, Cell.SOLID);
        world.set(2, 0, 0, Cell.PASSABLE);
        world.set(2, 1, 0, Cell.PASSABLE);

        assertEquals(
                Optional.of(new BlockCoord(2, 0, 0)),
                BreathSearch.nearest(world, new BlockCoord(0, 0, 0)),
                "рядом с водой рефлекс воздуха обязан вести на сушу (стоять на твёрдом полу), а не на "
                        + "поверхность воды: на поверхности дух снова тонет, тратит 216 [air] и простаивает");
    }

    @Test
    void nextStep_LiveFloodedShaft_GoesAroundDirtToAir() {
        CellWorld world = liveShaft();
        BlockCoord at = new BlockCoord(-294, 55, -483);

        for (int step = 0; step < 24; step++) {
            Optional<BlockCoord> next = BreathSearch.nextStep(world, at);
            assertTrue(
                    next.isPresent(),
                    "На шаге " + step + " из " + at + " воздух обязан быть достижим: дух тонет под уступом земли, "
                            + "как spirit-2 в (-294,55,-483) 01.11");
            BlockCoord cell = next.orElseThrow();
            Cell kind = world.cellAt(cell);
            Cell head = world.cellAt(new BlockCoord(cell.x(), cell.y() + 1, cell.z()));
            assertTrue(
                    kind == Cell.WATER || kind == Cell.PASSABLE,
                    "Шаг " + step + " ведёт в " + kind + " " + cell + ": путь к воздуху обязан идти по воде или "
                            + "пустоте, а не в уступ земли над головой — иначе дух упирается и тонет");
            assertTrue(
                    head == Cell.WATER || head == Cell.PASSABLE,
                    "Шаг " + step + " ставит голову в " + head + " над " + cell + ": дух ростом два блока под "
                            + "уступом земли не поднимется и утонет, как spirit-2 в observe16 N5");
            at = cell;
        }

        assertTrue(
                BreathSearch.nextStep(world, at).filter(at::equals).isPresent(),
                "За 24 шага из затонувшей клетки дух обязан выйти к сухой стоянке у воды или к воздуху, "
                        + "а не кружить под уступом: фактически " + at);
    }

    private static CellWorld liveShaft() {
        CellWorld world = new CellWorld();
        for (int x = -300; x <= -288; x++) {
            for (int y = 45; y <= 63; y++) {
                for (int z = -490; z <= -476; z++) {
                    world.set(x, y, z, Cell.SOLID);
                }
            }
        }
        for (int x = -300; x <= -288; x++) {
            for (int y = 64; y <= 70; y++) {
                for (int z = -490; z <= -476; z++) {
                    if (x == -300 || x == -288 || z == -490 || z == -476) {
                        world.set(x, y, z, Cell.SOLID);
                    }
                }
            }
        }
        try (InputStream in = BreathSearchTests.class.getResourceAsStream("/live/drownfall/shaft.txt")) {
            assertTrue(in != null, "Фикстура /live/drownfall/shaft.txt обязана быть в тестовых ресурсах");
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .forEach(line -> {
                        StringTokenizer parts = new StringTokenizer(line);
                        world.set(
                                Integer.parseInt(parts.nextToken()),
                                Integer.parseInt(parts.nextToken()),
                                Integer.parseInt(parts.nextToken()),
                                cell(parts.nextToken().charAt(0)));
                    });
        } catch (IOException error) {
            throw new UncheckedIOException("Фикстура /live/drownfall/shaft.txt не прочитана", error);
        }
        return world;
    }

    private static Cell cell(char code) {
        return switch (code) {
            case 'W' -> Cell.WATER;
            case 'A' -> Cell.PASSABLE;
            default -> Cell.SOLID;
        };
    }

    @Test
    void nearest_BankOneAboveWater_FindsStandByFloorWater() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.WATER).set(1, 0, 0, Cell.SOLID);

        assertEquals(
                Optional.of(new BlockCoord(1, 1, 0)),
                BreathSearch.nearest(world, new BlockCoord(0, 0, 0)),
                "Берег на блок выше воды (observe17: озеро -324,55,-472, суша y62-63) обязан находиться: "
                        + "вода касается пола стоянки, а не её ног; иначе дух остаётся в озере");
    }

    @Test
    void nearest_WideLakeSurface_FindsFarBankBeyondOldBudget() {
        CellWorld world = new CellWorld();
        for (int x = 0; x <= 60; x++) {
            for (int z = 0; z <= 60; z++) {
                world.set(x, 0, z, Cell.WATER);
            }
        }
        world.set(61, -1, 30, Cell.SOLID);

        assertEquals(
                Optional.of(new BlockCoord(61, 0, 30)),
                BreathSearch.nearest(world, new BlockCoord(0, 0, 0)),
                "Широкое озеро 61×61: берег в 91 шаге обязан находиться поиском по поверхности. "
                        + "Старый бюджет 512 уходил в объём воды и возвращал поверхность — дух кружил в озере "
                        + "(observe17 A2, [air]=781)");
    }
}
