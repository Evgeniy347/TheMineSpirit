package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.reload.DataTables;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PathAuditTests {
    private static final BlockCoord FEET = new BlockCoord(0, 69, 0);
    private static final BlockCoord PATH = new BlockCoord(1, 68, 1);
    private static final String DIRT_PATH = "dirt_path";

    @Test
    void streets_FloatingPathAboveLevel_NotCountedAsPlaced() {
        ScriptCall call = new ScriptCall(
                "build_schematic", Map.of("file", "street5_straight", "x", FEET.x(), "y", FEET.y(), "z", FEET.z()));

        BuildGap.Gap gap = BuildGap.trace(call, "oak", new FloatingPathProbe(), new PresetSchemes());

        assertEquals(
                0,
                gap.placed().getOrDefault("dirt", 0),
                "Тропа, висящая блоком над улицей, — не покрытие: аудит обязан требовать тропу на уровне земли. "
                        + "Поставлено: " + gap.placed() + ", не хватает: " + gap.missingCells()
                        + " Иначе тест streets_FloatingPathAboveLevel_NotCountedAsPlaced перестанет ловить эту поломку. При смене streets_FloatingPathAboveLevel_NotCountedAsPlaced проверь это утверждение.");
        assertEquals(
                "minecraft:" + DIRT_PATH,
                gap.missingCells().get(PATH),
                "Клетка улицы на уровне земли пуста, а тропа висит над ней: аудит обязан назвать недостающую "
                        + "клетку " + PATH + ". Не хватает: " + gap.missingCells()
                        + " Иначе тест streets_FloatingPathAboveLevel_NotCountedAsPlaced перестанет ловить эту поломку. При смене streets_FloatingPathAboveLevel_NotCountedAsPlaced проверь это утверждение.");
    }

    @Test
    void streets_PathInSurface_CountedAsPlaced() {
        ScriptCall call = new ScriptCall(
                "build_schematic", Map.of("file", "street5_straight", "x", FEET.x(), "y", FEET.y(), "z", FEET.z()));

        BuildGap.Gap gap = BuildGap.trace(call, "oak", new SurfacePathProbe(), new PresetSchemes());

        assertEquals(
                pathColumns().size(),
                gap.placed().getOrDefault("dirt", 0),
                "Все клетки тропы в поверхности — это готовая улица: " + gap.missingCells()
                        + " Иначе тест streets_PathInSurface_CountedAsPlaced перестанет ловить эту поломку. При смене streets_PathInSurface_CountedAsPlaced проверь это утверждение.");
    }

    @Test
    void streets_AllSunkPaths_DoNotCountAsUnbuilt() {
        ScriptCall call = new ScriptCall(
                "build_schematic", Map.of("file", "street5_straight", "x", FEET.x(), "y", FEET.y(), "z", FEET.z()));

        BuildGap.Gap gap = BuildGap.trace(call, "oak", new AllSunkPathProbe(), new PresetSchemes());

        assertEquals(
                10,
                gap.done(),
                "Гравийные края на месте — готова только часть улицы: " + gap.missing()
                        + " Иначе тест streets_AllSunkPaths_DoNotCountAsUnbuilt перестанет ловить эту поломку. При смене streets_AllSunkPaths_DoNotCountAsUnbuilt проверь это утверждение.");
        assertEquals(
                15,
                gap.shortage(),
                "Все пятнадцать троп утоплены на блок ниже клетки плана — это работа по подъёму: " + gap.missing()
                        + " Иначе тест streets_AllSunkPaths_DoNotCountAsUnbuilt перестанет ловить эту поломку. При смене streets_AllSunkPaths_DoNotCountAsUnbuilt проверь это утверждение.");
        assertEquals(
                15,
                gap.lifted().size(),
                "Утопленные тропы в недостаче помечены как подъём: " + gap.lifted()
                        + " Иначе тест streets_AllSunkPaths_DoNotCountAsUnbuilt перестанет ловить эту поломку. При смене streets_AllSunkPaths_DoNotCountAsUnbuilt проверь это утверждение.");
        assertEquals(
                0,
                gap.structuralShortage(),
                "За улицей нет ни одной потерянной клетки: вся недостача — перекос троп, а не дыры"
                        + " Иначе тест streets_AllSunkPaths_DoNotCountAsUnbuilt перестанет ловить эту поломку. При смене streets_AllSunkPaths_DoNotCountAsUnbuilt проверь это утверждение.");
        assertFalse(
                StageAudit.unbuilt(gap.structuralShortage(), gap.total(), 50),
                "Перекос тропы — ремонт, а не разрушение: улицу не перестраивают заново из-за поднятия троп"
                        + " Иначе тест streets_AllSunkPaths_DoNotCountAsUnbuilt перестанет ловить эту поломку. При смене streets_AllSunkPaths_DoNotCountAsUnbuilt проверь это утверждение.");
    }

    @Test
    void streets_EmptyPathCells_StillUnbuilt() {
        ScriptCall call = new ScriptCall(
                "build_schematic", Map.of("file", "street5_straight", "x", FEET.x(), "y", FEET.y(), "z", FEET.z()));

        BuildGap.Gap gap = BuildGap.trace(call, "oak", new EmptyPathCellsProbe(), new PresetSchemes());

        assertEquals(
                15,
                gap.shortage(),
                "Пятнадцати троп нет вовсе: " + gap.missing()
                        + " Иначе тест streets_EmptyPathCells_StillUnbuilt перестанет ловить эту поломку. При смене streets_EmptyPathCells_StillUnbuilt проверь это утверждение.");
        assertEquals(
                0,
                gap.lifted().size(),
                "Старой тропы ниже клетки нет — поднимать нечего: " + gap.lifted()
                        + " Иначе тест streets_EmptyPathCells_StillUnbuilt перестанет ловить эту поломку. При смене streets_EmptyPathCells_StillUnbuilt проверь это утверждение.");
        assertEquals(
                15,
                gap.structuralShortage(),
                "Все пятнадцать троп — настоящая недостача улицы"
                        + " Иначе тест streets_EmptyPathCells_StillUnbuilt перестанет ловить эту поломку. При смене streets_EmptyPathCells_StillUnbuilt проверь это утверждение.");
        assertTrue(
                StageAudit.unbuilt(gap.structuralShortage(), gap.total(), 50),
                "Пустая клетка тропы без старой тропы ниже — недостроенная улица, её этап переоткрывается"
                        + " Иначе тест streets_EmptyPathCells_StillUnbuilt перестанет ловить эту поломку. При смене streets_EmptyPathCells_StillUnbuilt проверь это утверждение.");
    }

    private static final class AllSunkPathProbe implements BuildGap.Probe {
        @Override
        public String block(BlockCoord at) {
            if (at.y() == FEET.y() - 1) {
                return pathColumns().contains(new BlockCoord(at.x(), 0, at.z()))
                        ? "minecraft:dirt"
                        : "minecraft:gravel";
            }
            return "minecraft:air";
        }

        @Override
        public String rawBlock(BlockCoord at) {
            if (at.y() == FEET.y() - 2 && pathColumns().contains(new BlockCoord(at.x(), 0, at.z()))) {
                return "minecraft:" + DIRT_PATH;
            }
            if (at.y() == FEET.y() - 1) {
                return pathColumns().contains(new BlockCoord(at.x(), 0, at.z())) ? "minecraft:air" : "minecraft:gravel";
            }
            return "minecraft:stone";
        }

        @Override
        public boolean passable(BlockCoord at) {
            return false;
        }
    }

    private static final class EmptyPathCellsProbe implements BuildGap.Probe {
        @Override
        public String block(BlockCoord at) {
            if (at.y() == FEET.y() - 1) {
                return pathColumns().contains(new BlockCoord(at.x(), 0, at.z()))
                        ? "minecraft:dirt"
                        : "minecraft:gravel";
            }
            return "minecraft:air";
        }

        @Override
        public String rawBlock(BlockCoord at) {
            if (at.y() == FEET.y() - 1) {
                return pathColumns().contains(new BlockCoord(at.x(), 0, at.z())) ? "minecraft:air" : "minecraft:gravel";
            }
            return "minecraft:stone";
        }

        @Override
        public boolean passable(BlockCoord at) {
            return false;
        }
    }

    private static final class FloatingPathProbe implements BuildGap.Probe {
        @Override
        public String block(BlockCoord at) {
            if (at.y() == FEET.y() && pathColumns().contains(new BlockCoord(at.x(), 0, at.z()))) {
                return "minecraft:" + DIRT_PATH;
            }
            return "minecraft:grass_block";
        }

        @Override
        public boolean passable(BlockCoord at) {
            return false;
        }
    }

    @Test
    void streets_SunkPathBelowLevel_NotCounted() {
        ScriptCall call = new ScriptCall(
                "build_schematic", Map.of("file", "street5_straight", "x", FEET.x(), "y", FEET.y(), "z", FEET.z()));

        BuildGap.Gap gap = BuildGap.trace(call, "oak", new SunkMappedPathProbe(), new PresetSchemes());

        assertEquals(
                0,
                gap.placed().getOrDefault("dirt", 0),
                "Тропа, утопленная ниже уровня схемы, — ступенька, а не покрытие: аудит обязан требовать тропу в "
                        + "клетке плана. Поставлено: " + gap.placed() + ", не хватает: " + gap.missingCells()
                        + " Иначе тест streets_SunkPathBelowLevel_NotCounted перестанет ловить эту поломку. При смене streets_SunkPathBelowLevel_NotCounted проверь это утверждение.");
        assertEquals(
                "minecraft:" + DIRT_PATH,
                gap.missingCells().get(PATH),
                "Клетка улицы на уровне плана пуста, а тропа лежит ниже: аудит обязан назвать недостающую клетку "
                        + PATH + ". Не хватает: " + gap.missingCells()
                        + " Иначе тест streets_SunkPathBelowLevel_NotCounted перестанет ловить эту поломку. При смене streets_SunkPathBelowLevel_NotCounted проверь это утверждение.");
    }

    private static final class SunkMappedPathProbe implements BuildGap.Probe {
        @Override
        public String block(BlockCoord at) {
            String raw = rawBlock(at);
            return DIRT_PATH.equals(SchematicBlocks.path(raw)) ? "minecraft:dirt" : raw;
        }

        @Override
        public String rawBlock(BlockCoord at) {
            if (at.y() == FEET.y() - 2 && pathColumns().contains(new BlockCoord(at.x(), 0, at.z()))) {
                return "minecraft:" + DIRT_PATH;
            }
            return "minecraft:air";
        }

        @Override
        public boolean passable(BlockCoord at) {
            return at.y() == FEET.y() - 1;
        }
    }

    private static final class SurfacePathProbe implements BuildGap.Probe {
        @Override
        public String block(BlockCoord at) {
            if (at.y() == FEET.y() - 1 && pathColumns().contains(new BlockCoord(at.x(), 0, at.z()))) {
                return "minecraft:" + DIRT_PATH;
            }
            return "minecraft:air";
        }

        @Override
        public boolean passable(BlockCoord at) {
            return at.y() != FEET.y() - 1;
        }
    }

    private static Set<BlockCoord> pathColumns() {
        Schematic street = DataTables.INSTANCE
                .presets()
                .current()
                .byId("street5_straight")
                .orElseThrow()
                .schematic();
        Set<BlockCoord> columns = new HashSet<>();
        for (Schematic.PlacedEntry entry : street.entries(FEET, BuildRotation.NONE)) {
            if (DIRT_PATH.equals(SchematicBlocks.path(entry.block()))) {
                columns.add(new BlockCoord(entry.pos().x(), 0, entry.pos().z()));
            }
        }
        return Set.copyOf(columns);
    }

    private static final class PresetSchemes implements BuildGap.Schemes {
        @Override
        public Optional<MineLayout> mine(ScriptCall call) {
            return Optional.empty();
        }

        @Override
        public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
            if (!"build_schematic".equals(call.script())
                    || !(call.params().get("file") instanceof String file)
                    || !(call.params().get("x") instanceof Integer x)
                    || !(call.params().get("y") instanceof Integer y)
                    || !(call.params().get("z") instanceof Integer z)) {
                return Optional.empty();
            }
            BuildRotation rotation = call.params().get("rotation") instanceof String turn
                    ? BuildRotation.parse(turn)
                    : BuildRotation.NONE;
            return Optional.of(DataTables.INSTANCE
                    .presets()
                    .current()
                    .byId(file)
                    .orElseThrow()
                    .schematic()
                    .entries(new BlockCoord(x, y, z), rotation));
        }
    }
}
