package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningScenarioTests {
    private static final int FLOOR_Y = -1;
    private static final int FEET_Y = 0;
    private static final int ENTRANCE_X = 0;
    private static final int END_X = 14;

    @Test
    void mine_ThreeSpiritsHikeToVein_AllSafeOreDugAndHazardsLeftAlone() {
        CellWorld world = tunnel();
        List<BlockCoord> remaining =
                new ArrayList<>(List.of(ore(3, 1), ore(4, 1), ore(6, -1), ore(7, -1), ore(9, 1), ore(10, -1)));
        remaining.addAll(List.of(ore(12, 1), sealed(12, -1)));
        world.set(12, 1, 1, Cell.WATER);
        List<BlockCoord> spirits = new ArrayList<>(List.of(
                new BlockCoord(ENTRANCE_X, FEET_Y, 0),
                new BlockCoord(ENTRANCE_X + 1, FEET_Y, 0),
                new BlockCoord(ENTRANCE_X + 2, FEET_Y, 0)));
        List<BlockCoord> safe = List.of(ore(3, 1), ore(4, 1), ore(6, -1), ore(7, -1), ore(9, 1), ore(10, -1));

        Map<Integer, Integer> minedBy = new LinkedHashMap<>();
        Set<BlockCoord> stands = new HashSet<>();
        Set<BlockCoord> dug = new HashSet<>();
        int rounds = 0;
        while (!remaining.isEmpty() && rounds++ < 20) {
            List<Choice> chosen = chooseRound(spirits, remaining, world);
            if (chosen.isEmpty()) {
                break;
            }
            applyRound(chosen, spirits, remaining, world, minedBy, stands, dug);
        }

        assertEquals(
                new HashSet<>(safe),
                dug,
                "Добыты не все безопасные цели: поход трёх духов не разобрал жилу " + remaining);
        for (BlockCoord cell : safe) {
            assertEquals(
                    Cell.PASSABLE,
                    world.peek(cell),
                    "Безопасная руда " + cell + " не добыта: поход трёх духов не дошёл до жилы");
        }
        assertEquals(
                Cell.SOLID, world.peek(ore(12, 1)), "Руда у воды цела: ломка открыла бы воду в выработку и на духа");
        assertEquals(
                Cell.SOLID,
                world.peek(sealed(12, -1)),
                "Замурованная руда цела: к ней нет стоянки, дух не бьётся в блок без подхода");
        assertEquals(
                3,
                minedBy.size(),
                "Руду разобрали три духа, а не один: распределение " + minedBy + " оставило двух без работы");
        for (BlockCoord stand : stands) {
            assertEquals(
                    Cell.SOLID,
                    world.peek(new BlockCoord(stand.x(), FLOOR_Y, stand.z())),
                    "Стоянка " + stand + " без пола: дух упадёт или зависнет");
        }
    }

    private static List<Choice> chooseRound(List<BlockCoord> spirits, List<BlockCoord> remaining, CellWorld world) {
        List<Choice> chosen = new ArrayList<>();
        Set<BlockCoord> takenTargets = new HashSet<>();
        Set<BlockCoord> excluded = new HashSet<>();
        for (int index = 0; index < spirits.size(); index++) {
            MiningPlanner.Outcome outcome =
                    MiningPlanner.next(spirits.get(index), remaining, world, excluded, stand -> true);
            if (!(outcome instanceof MiningPlanner.Next next)) {
                continue;
            }
            if (takenTargets.contains(next.target())) {
                continue;
            }
            assertTrue(
                    MiningSafety.canBreak(next.target(), world) instanceof BreakSafety.Safe,
                    "Планировщик выбрал небезопасную цель " + next.target() + ": дух затопит выработку");
            assertFalse(
                    excluded.contains(next.stand()),
                    "Два духа встали на одну стоянку " + next.stand() + ": второй не сможет копать");
            takenTargets.add(next.target());
            excluded.add(next.stand());
            chosen.add(new Choice(index, next));
        }
        return chosen;
    }

    private static void applyRound(
            List<Choice> chosen,
            List<BlockCoord> spirits,
            List<BlockCoord> remaining,
            CellWorld world,
            Map<Integer, Integer> minedBy,
            Set<BlockCoord> stands,
            Set<BlockCoord> dug) {
        for (Choice choice : chosen) {
            MiningPlanner.Next next = choice.next();
            assertTrue(
                    MiningSafety.canStand(next.stand(), world),
                    "Дух " + choice.spirit() + " выбрал стоянку " + next.stand() + " без пола или у опасности");
            assertTrue(
                    next.stand().y() - spirits.get(choice.spirit()).y() <= MiningTuning.MAX_STAND_RISE,
                    "Стоянка " + next.stand() + " выше духа больше предела: он туда не поднимется");
            spirits.set(choice.spirit(), next.stand());
            stands.add(next.stand());
            assertTrue(remaining.remove(next.target()), "Цель " + next.target() + " добыта дважды");
            world.set(next.target().x(), next.target().y(), next.target().z(), Cell.PASSABLE);
            minedBy.merge(choice.spirit(), 1, Integer::sum);
            dug.add(next.target());
        }
    }

    @Test
    void hike_SpiritWithoutPickaxe_RefusesHandDigAndDoesNotBreakStone() {
        CellWorld world = tunnel();
        BlockCoord target = ore(3, 1);
        world.set(target.x(), target.y(), target.z(), Cell.SOLID);

        ToolChoice.Choice hand = ToolChoice.best(List.of());
        assertFalse(hand.dropsAllowed(), "Без инструмента выбор обязан быть «руками, без добычи»");
        assertTrue(
                ToolChoice.refusesHandDig(hand, List.of(), true, false),
                "Дух без кирки отказывается копать камень руками: иначе поход без кирки портит руду и тратит время");
        assertFalse(
                ToolChoice.refusesHandDig(hand, List.of(), true, true),
                "Из запертой ямы дух камень руками всё-таки копает: выход важнее сохранности руды");

        ToolChoice.ToolOption pickaxe =
                new ToolChoice.ToolOption(0, 4.0, true, 100, 131, false, "minecraft:stone_pickaxe");
        ToolChoice.Choice withPickaxe = ToolChoice.best(List.of(pickaxe));
        assertTrue(withPickaxe.dropsAllowed(), "С киркой дух добывает руду с дропом");
        assertFalse(
                ToolChoice.refusesHandDig(withPickaxe, List.of(pickaxe), true, false),
                "С киркой в руке отказ от ломки камня — ошибка: поход обязан копать");

        assertEquals(
                Cell.SOLID,
                world.peek(target),
                "Отказ «без кирки» не трогает мир: блок остаётся на месте до прихода духа с инструментом");
    }

    private static BlockCoord ore(int x, int zSide) {
        return new BlockCoord(x, FEET_Y, zSide);
    }

    private static BlockCoord sealed(int x, int z) {
        return new BlockCoord(x, FEET_Y, z);
    }

    private static CellWorld tunnel() {
        CellWorld world = new CellWorld();
        for (int x = ENTRANCE_X - 1; x <= END_X + 1; x++) {
            world.set(x, FLOOR_Y, 0, Cell.SOLID);
            for (int z = -1; z <= 1; z++) {
                world.set(x, FEET_Y + 2, z, Cell.SOLID);
            }
            world.set(x, FEET_Y, -1, Cell.SOLID);
            world.set(x, FLOOR_Y, -1, Cell.SOLID);
            world.set(x, FEET_Y + 1, -1, Cell.SOLID);
            world.set(x, FEET_Y, 1, Cell.SOLID);
            world.set(x, FLOOR_Y, 1, Cell.SOLID);
            world.set(x, FEET_Y + 1, 1, Cell.SOLID);
            world.set(x, FEET_Y, 0, Cell.PASSABLE);
            world.set(x, FEET_Y + 1, 0, Cell.PASSABLE);
        }
        for (BlockCoord cell : List.of(ore(3, 1), ore(4, 1), ore(6, -1), ore(7, -1), ore(9, 1), ore(10, -1))) {
            world.set(cell.x(), cell.y(), cell.z(), Cell.SOLID);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.set(12 + dx, FEET_Y + dy, -1 + dz, Cell.SOLID);
                }
            }
        }
        world.set(12, FEET_Y, 1, Cell.SOLID);
        world.set(12, FEET_Y + 1, 1, Cell.WATER);
        return world;
    }

    private record Choice(int spirit, MiningPlanner.Next next) {}
}
