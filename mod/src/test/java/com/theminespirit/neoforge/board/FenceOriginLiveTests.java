package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import org.junit.jupiter.api.Test;

class FenceOriginLiveTests {
    private static final BlockCoord GROUND = new BlockCoord(-170, 70, -489);
    private static final BlockCoord ROOF = new BlockCoord(-170, 80, -489);

    @Test
    void fenceOrigin_DangerOnWarehouseRoof_UsesSettlementFenceLevel() throws IOException {
        StageSite site = TownFixtures.flatSite();
        Map<BlockCoord, String> cells = load("/live/fence-roof-1111.live");

        assertEquals(
                GROUND,
                SiteLevels.fenceOrigin(site, ROOF),
                "Точка опасности (-170,80,-489) лежит на крыше склада; основание забора обязано быть уровнем "
                        + "ограды поселения (-170,70,-489). Цена ошибки: дух снизу «в яме», DigOut ищет путь на y=80 "
                        + "полным перебором и падает — так 55 раз на 5000 строк лога и тик 3,4 с");
        assertEquals(
                "HARD",
                kind(cells, GROUND),
                "Живая фикстура обязана подтверждать: на уровне ограды под крышей склада есть твёрдая опора");
        assertEquals(
                "SOFT", kind(cells, ROOF), "Живая фикстура обязана подтверждать: y=80 — крыша склада, а не низ забора");
        assertEquals(
                "AIR",
                kind(cells, new BlockCoord(ROOF.x(), ROOF.y() + 1, ROOF.z())),
                "Над крышей склада воздух: постановка забора из клетки опасности шла именно на крышу");
    }

    @Test
    void fenceOrigin_DangerAlreadyAtFenceLevel_KeepsPoint() throws IOException {
        StageSite site = TownFixtures.flatSite();
        Map<BlockCoord, String> cells = load("/live/fence-roof-1111.live");

        assertEquals(
                GROUND,
                SiteLevels.fenceOrigin(site, GROUND),
                "Точка опасности уже на уровне ограды не двигается: иначе забор уезжает с площадки вниз или вверх");
        assertEquals(
                "HARD",
                kind(cells, GROUND),
                "Фикстура подтверждает, что точка на уровне ограды — реальная клетка мира, а не выдуманная высота");
    }

    private static String kind(Map<BlockCoord, String> cells, BlockCoord cell) {
        BlockCoord offset = new BlockCoord(cell.x() - GROUND.x(), cell.y() - GROUND.y(), cell.z() - GROUND.z());
        return cells.getOrDefault(offset, "AIR");
    }

    private static Map<BlockCoord, String> load(String resource) throws IOException {
        try (InputStream stream = FenceOriginLiveTests.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Нет живой фикстуры: " + resource);
            }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), resource);
        }
    }

    private static Map<BlockCoord, String> parse(BufferedReader reader, String source) throws IOException {
        Map<BlockCoord, String> cells = new HashMap<>();
        String line;
        int number = 0;
        while ((line = reader.readLine()) != null) {
            number++;
            if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                continue;
            }
            StringTokenizer parts = new StringTokenizer(line);
            if (parts.countTokens() != 4) {
                throw new IllegalArgumentException(source + ":" + number + ": строка не из четырёх полей: " + line);
            }
            BlockCoord offset = new BlockCoord(
                    Integer.parseInt(parts.nextToken()),
                    Integer.parseInt(parts.nextToken()),
                    Integer.parseInt(parts.nextToken()));
            cells.put(offset, parts.nextToken());
        }
        return cells;
    }
}
