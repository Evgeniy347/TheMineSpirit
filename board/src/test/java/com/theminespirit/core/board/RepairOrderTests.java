package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.RepairAudit.WorldView;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepairOrderTests {
    private static final String HUT = "hut_1";
    private static final String LIVE_BOARD = "/live/observe11-late-board.json";
    private static final BlockCoord HUT_ORIGIN = new BlockCoord(-181, 69, -513);
    private static final long NOW = 22_830_000L;

    @Test
    void audit_LiveHutMissingTwelfth_NeedsRepairWithoutRebuild() {
        Schematic hut = load(HUT);
        WorldView world = damagedWorld(hut, missingCells(hut));
        RepairAudit.Report report = RepairAudit.audit(
                hut, HUT_ORIGIN, BuildRotation.NONE, world, RepairData.defaults(), new HashMap<>(), NOW);

        assertTrue(
                report.damaged(),
                "Живая хижина observe11 на 88%: недостача клеток обязана попасть в отчёт обхода, иначе "
                        + "повреждение не видно никому");
        assertFalse(
                report.rebuild(),
                "Потеряна восьмая часть дома (88% целого), а не больше половины: дом чинят, а не перестраивают уровень");
        Map<String, Integer> need = RepairAudit.need(report);
        assertEquals(
                report.missing(),
                need.values().stream().mapToInt(Integer::intValue).sum(),
                "Материалы ремонта считаются по каждой недостающей клетке схемы: без них задание ремонта "
                        + "не примут на доске и хижина останется 88%");
        assertFalse(need.isEmpty(), "Аудит повреждённого дома обязан вернуть непустой набор материалов ремонта");
    }

    @Test
    void repairTicket_LiveBoardWithoutHutRepair_AcceptedAsPendingRepair() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        assertEquals(
                Optional.of(BoardStatus.DONE),
                board.statusOf("first:build_house"),
                "Живая доска observe11 считает хижину построенной: ремонт идёт поверх DONE");
        assertFalse(
                board.find("repair:build_house").isPresent(),
                "Живая доска не открыла ремонт хижины при 88%: именно поэтому повреждение стояло без работы");
        Map<String, Integer> need = Map.of("oak_planks", 12, "jungle_log", 2);

        BoardTicket ticket = RepairPlan.ticket(BoardCase.BUILD_HOUSE, need, NOW);

        assertEquals("repair:build_house", ticket.id(), "Ремонт дома идёт одним тикетом repair:build_house");
        assertEquals(BoardCase.REPAIR_BUILDING, ticket.boardCase(), "Тикет ремонта обязан нести кейс REPAIR_BUILDING");
        assertEquals(
                BoardOutcome.ACCEPTED, board.submit(ticket), "Ремонт поверх построенной хижины обязан принять доску");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(ticket.id()),
                "Принятый ремонт встаёт в очередь и его выдадут духу");
        assertEquals(
                need,
                board.find(ticket.id()).orElseThrow().requiredItems(),
                "Тикет несёт материалы недостающих клеток");
    }

    private static Schematic load(String id) {
        String path = PresetLibrary.SCHEMATICS_ROOT + id + SchematicTextParser.EXTENSION;
        return SchematicTextParser.parse(JsonResource.readClasspath(path), path).schematic();
    }

    private static Set<BlockCoord> missingCells(Schematic hut) {
        List<BlockCoord> expected = new ArrayList<>();
        for (Schematic.PlacedEntry entry : hut.entries(HUT_ORIGIN, BuildRotation.NONE)) {
            if (!entry.optional()) {
                expected.add(entry.pos());
            }
        }
        assertTrue(expected.size() > 16, "Схема " + HUT + " слишком мала для проверки повреждения: " + expected.size());
        Set<BlockCoord> missing = new LinkedHashSet<>();
        for (int index = 0; index < expected.size(); index += 8) {
            missing.add(expected.get(index));
        }
        return missing;
    }

    private static WorldView damagedWorld(Schematic hut, Set<BlockCoord> missing) {
        Map<BlockCoord, String> placed = new HashMap<>();
        for (Schematic.PlacedEntry entry : hut.entries(HUT_ORIGIN, BuildRotation.NONE)) {
            placed.put(entry.pos(), entry.block());
        }
        missing.forEach(placed::remove);
        return new WorldView() {
            @Override
            public String blockAt(BlockCoord cell) {
                return placed.getOrDefault(cell, "minecraft:air");
            }

            @Override
            public boolean playerPlaced(BlockCoord cell) {
                return false;
            }
        };
    }
}
