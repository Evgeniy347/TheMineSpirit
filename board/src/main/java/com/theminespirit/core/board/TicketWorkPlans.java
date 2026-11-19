package com.theminespirit.core.board;

import static com.theminespirit.core.board.TicketPlans.*;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.api.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TicketWorkPlans {
    private TicketWorkPlans() {}

    static TaskNode fishWorkPoint(TicketTarget target) {
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "fish",
                        Map.of(
                                COUNT,
                                CatalogLimits.FISH_COUNT_DEFAULT,
                                "duration_ticks",
                                CatalogLimits.FISH_DURATION_DEFAULT,
                                RADIUS,
                                CatalogLimits.FISH_RADIUS_DEFAULT)));
    }

    static int torchCount(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : plans().torchesPerCraft();
        return Math.min(count, CatalogLimits.COUNT_MAX);
    }

    static TaskNode smeltCharcoal(TicketTarget target) {
        String log = target.supplyItem() == null ? "oak_log" : target.supplyItem();
        int logs = target.supplyCount() > 0 ? target.supplyCount() : plans().charcoalLogs();
        int charcoal = target.count() > 0 ? target.count() : plans().charcoalBatch();
        return sequence(
                gotoTarget(target),
                new ScriptCall(ACQUIRE, Map.of(ITEM, log, COUNT, Math.min(logs, CatalogLimits.COUNT_MAX))),
                new ScriptCall(
                        "smelt",
                        Map.of(ITEM, "charcoal", COUNT, charcoal, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)),
                store(target, Leftovers.ITEMS.toArray(String[]::new)));
    }

    static TaskNode store(TicketTarget target, String... items) {
        List<TaskNode> steps = new ArrayList<>(items.length + 1);
        steps.add(gotoTarget(target));
        for (String item : items) {
            steps.add(new FallbackNode(List.of(
                    new ScriptCall(
                            "deposit",
                            Map.of(
                                    CONTAINER,
                                    "auto",
                                    ITEM,
                                    item,
                                    COUNT,
                                    CatalogLimits.COUNT_MAX,
                                    RADIUS,
                                    plans().supplyContainerRadius(),
                                    "leftover",
                                    true)),
                    new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
        }
        return new SequenceNode(steps);
    }

    static TaskNode dismantleShelter(TicketTarget target) {
        return new ScriptCall(
                CLEAR_AREA,
                Map.of(
                        "x1",
                        target.target().x() - plans().dismantleRadius(),
                        "y1",
                        target.target().y(),
                        "z1",
                        target.target().z() - plans().dismantleRadius(),
                        "x2",
                        target.target().x() + plans().dismantleRadius(),
                        "y2",
                        target.target().y() + 2,
                        "z2",
                        target.target().z() + plans().dismantleRadius()));
    }

    static TaskNode collectDrops(TicketTarget target) {
        Map<String, Object> params = target.dropItems().isEmpty()
                ? Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT)
                : Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT, "filter", target.dropItems());
        return sequence(gotoPoint(target.target()), new ScriptCall("collect_drops", params));
    }

    static TaskNode buildOrchard(TicketTarget target) {
        StageWork work = target.work();
        StageSite site = work == null ? null : work.site();
        String scheme = StageRules.current()
                .scheme(BoardCase.BUILD_ORCHARD, 1)
                .orElseThrow(() -> new IllegalStateException(
                        "stages.json: нет схемы сада уровня 1 — постройке сада не по чему строиться"));
        BlockCoord origin = site == null ? target.target() : site.orchardOrigin(target.building());
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        List<TaskNode> steps = new ArrayList<>();
        BlockCoord yard = site == null ? target.target() : site.workYard();
        steps.add(new FallbackNode(
                List.of(gotoPoint(yard), new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
        TorchPlans.addMaterials(steps, orchardMaterials(scheme), wood, target);
        steps.add(schematic(scheme, origin, target));
        return new SequenceNode(steps);
    }

    static Map<String, Integer> orchardMaterials(String scheme) {
        Schematic schematic = PresetLibrary.current()
                .byId(scheme)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Нет схемы '" + scheme + "' для сада: проверьте presets.json и файл схемы"))
                .schematic();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (!entry.optional() && !SchematicActions.isNotBought(entry.block(), entry.state())) {
                counts.merge(SchematicBlocks.itemOf(entry.block()), 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    static TaskNode buildHouse(int level, TicketTarget target) {
        String scheme = target.schemeFile() == null
                ? BuildingLevels.scheme(BoardCase.BUILD_HOUSE, level).orElse(DEFAULT_SCHEME)
                : target.schemeFile();
        String previous = level > 1
                ? BuildingLevels.scheme(BoardCase.BUILD_HOUSE, level - 1).orElse(null)
                : null;
        return sequence(gotoTarget(target), schematic(scheme, target, previous));
    }

    static TaskNode repairBuilding(TicketTarget target) {
        if (target.schemeFile() == null && target.leveling() != null) {
            return LevelRepair.plan(target);
        }
        if (target.schemeFile() == null) {
            throw new IllegalArgumentException("Ремонт без схемы постройки и без выравнивания: чинят по схеме той "
                    + "постройки, что повреждена (StageWork.repairs или файл схемы); цель задания потеряна — ждать, "
                    + "пока аудит этапа её восстановит");
        }
        return sequence(gotoTarget(target), schematic(target.schemeFile(), target));
    }

    static TaskNode repairTool(TicketTarget target) {
        if (target.craftItem() == null) {
            throw new IllegalArgumentException("Задание ремонта инструмента без предмета: скрипт repair_tool "
                    + "чинит конкретный изношенный предмет, без имени чинить нечего");
        }
        return sequence(gotoTarget(target), new ScriptCall("repair_tool", Map.of(ITEM, target.craftItem())));
    }
}
