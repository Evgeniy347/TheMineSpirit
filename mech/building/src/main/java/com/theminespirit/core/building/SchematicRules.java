package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SchematicRules {
    public enum RuleId {
        CLOSED_WALLS("closed_walls"),
        ENTRANCE("entrance"),
        ROOF("roof"),
        INNER_HEIGHT("inner_height"),
        WINDOW("window"),
        DOOR("door"),
        SUPPORT("support"),
        CHEST_OPENABLE("chest_openable"),
        CHEST_PAIR("chest_pair"),
        LIGHT("light"),
        ENTRANCE_STEP("entrance_step"),
        LEVEL_CEILING("level_ceiling"),
        WINDOW_SIZE("window_size"),
        DECOR("decor"),
        REACHABLE("reachable"),
        STAIRS("stairs"),
        STAIR_APPROACH("stair_approach"),
        RAILING("railing"),
        PANE("pane"),
        DOUBLE_DOOR("double_door"),
        FIREPLACE("fireplace"),
        PASSAGE("passage"),
        FLOOR("floor"),
        PASSAGE_HEIGHT("passage_height"),
        PASSAGE_STEP("passage_step");

        private final String id;

        RuleId(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<RuleId> byId(String id) {
            Objects.requireNonNull(id, "Id правила не может быть null: по нему ищут правило схемы");
            for (RuleId rule : values()) {
                if (rule.id.equals(id)) {
                    return Optional.of(rule);
                }
            }
            return Optional.empty();
        }
    }

    public record Structure(String id, BuildingType type, Schematic schematic, BlockCoord entry, int level) {
        public Structure(String id, BuildingType type, Schematic schematic, BlockCoord entry) {
            this(id, type, schematic, entry, Preset.LEVEL_NONE);
        }

        public Structure {
            Objects.requireNonNull(id, "Id схемы не может быть null: по нему называют нарушителя");
            if (id.isBlank()) {
                throw new IllegalArgumentException("Id схемы пуст: по пустому имени нарушителя не найти");
            }
            Objects.requireNonNull(type, "Тип строения не может быть null: от него зависят правила жилья");
            Objects.requireNonNull(schematic, "Схема не может быть null: проверять нечего");
            Objects.requireNonNull(entry, "Точка входа не может быть null: без неё вход не проверить");
            if (entry.x() < 0
                    || entry.y() < 0
                    || entry.z() < 0
                    || entry.x() >= schematic.sizeX()
                    || entry.y() >= schematic.sizeY()
                    || entry.z() >= schematic.sizeZ()) {
                throw new IllegalArgumentException("Точка входа " + entry + " схемы '" + id + "' вне габаритов "
                        + schematic.sizeX() + "×" + schematic.sizeY() + "×" + schematic.sizeZ()
                        + ": по такой клетке вход не проверить");
            }
        }
    }

    public record Violation(RuleId rule, String message) {
        public Violation {
            Objects.requireNonNull(rule, "Правило нарушения не может быть null: без него неизвестно, что чинить");
            Objects.requireNonNull(message, "Сообщение нарушения не может быть null: без него не видно причину");
        }
    }

    private SchematicRules() {}

    public static List<Violation> check(Structure structure) {
        Objects.requireNonNull(structure, "Строение не может быть null: проверять нечего");
        SchematicBody body = SchematicBody.of(structure.schematic(), structure.entry());
        Structure walls = body.covers(structure.schematic())
                ? structure
                : new Structure(
                        structure.id(),
                        structure.type(),
                        body.crop(structure.schematic()),
                        body.local(structure.entry()));
        Map<BlockCoord, String> grid = SchematicCells.of(walls.schematic());
        List<Violation> violations = new ArrayList<>();
        SchematicShell.checkClosedWalls(walls, grid, violations);
        SchematicShell.checkEntrance(walls, grid, violations);
        EntranceStep.problem(structure.schematic(), body, structure.entry())
                .ifPresent(problem -> violations.add(new Violation(RuleId.ENTRANCE_STEP, problem)));
        SchematicShell.checkRoof(walls, grid, violations);
        SchematicShell.checkInnerHeight(walls, grid, violations);
        int level = structure.level();
        if (structure.type() == BuildingType.HOUSING) {
            SchematicFittings.checkWindow(walls, grid, violations);
            SchematicFittings.checkDoor(walls, grid, violations);
            if (level > Preset.LEVEL_NONE) {
                EntrancePorch.problem(structure.schematic(), body, structure.entry())
                        .ifPresent(problem -> violations.add(new Violation(RuleId.ENTRANCE_STEP, problem)));
                SchematicShell.checkLevelCeiling(walls, grid, level, violations);
                SchematicLevelRules.checkWindowSize(walls.schematic(), grid, level, violations);
            }
        }
        SchematicFittings.checkLight(walls.schematic(), violations);
        Map<BlockCoord, String> full = SchematicCells.of(structure.schematic());
        SchematicFittings.checkPanes(full, violations);
        if (level > Preset.LEVEL_NONE
                && (structure.type() == BuildingType.HOUSING || structure.type() == BuildingType.SOCIAL)) {
            SchematicLevelRules.checkDecor(structure.schematic(), full, level, violations);
        }
        SchematicWalk.Result walk = SchematicWalk.walk(grid, walls.schematic(), walls.entry());
        SchematicLevelRules.checkReachable(grid, walk, violations);
        SchematicLevelRules.checkStairs(walls, grid, walk, level, violations);
        SchematicWalk.Result fullWalk = SchematicWalk.walk(full, structure.schematic(), structure.entry());
        SchematicWideApproach.check(structure, full, fullWalk, violations);
        SchematicRailing.check(structure, full, fullWalk, violations);
        DoorPassage.problem(structure.schematic(), SchematicWalk.walk(full, structure.schematic(), structure.entry()))
                .ifPresent(problem -> violations.add(new Violation(RuleId.PASSAGE, problem)));
        SchematicFittings.checkSupport(structure.schematic(), full, violations);
        if (violations.stream().noneMatch(violation -> violation.rule() == RuleId.SUPPORT)) {
            SchematicFittings.checkMounting(structure.schematic(), full, violations);
        }
        SchematicFittings.checkChests(full, violations);
        DoorPairs.problem(structure.schematic())
                .ifPresent(problem -> violations.add(new Violation(RuleId.DOUBLE_DOOR, problem)));
        SchematicShell.checkFireplace(walls, grid, violations);
        ChestPairs.problem(structure.schematic())
                .ifPresent(problem -> violations.add(new Violation(RuleId.CHEST_PAIR, problem)));
        return List.copyOf(violations);
    }
}
