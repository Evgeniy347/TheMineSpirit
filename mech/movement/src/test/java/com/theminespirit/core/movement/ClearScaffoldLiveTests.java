package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.ClearScaffold;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ClearScaffoldLiveTests {
    private static final int MAX_STAND_RISE = 3;

    @Test
    void livePillars_LeaseCellsAreNotTargets_ClimbCountedFromTop() throws IOException {
        Map<BlockCoord, BlockKind> world = LiveFixture.load("/live/clearloop-pillars-1011.live");
        Set<BlockCoord> solid = world.entrySet().stream()
                .filter(entry -> entry.getValue() != BlockKind.AIR)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<BlockCoord> top = solid.stream().filter(cell -> cell.y() > 0).collect(Collectors.toSet());
        assertFalse(top.isEmpty(), "Живая фикстура должна содержать высокие клетки — иначе это не случай столбов");

        Set<BlockCoord> own =
                solid.stream().filter(cell -> cell.x() == 0 && cell.z() == -1).collect(Collectors.toSet());
        assertFalse(own.isEmpty(), "Свой столб в фикстуре: подмости духа на его колонке");
        List<BlockCoord> targets = ClearScaffold.withoutLeased(new ArrayList<>(solid), own::contains);
        assertEquals(
                solid.size() - own.size(),
                targets.size(),
                "Клетки своей аренды не цели расчистки: иначе дух ломает только что поставленную опору");
        assertTrue(targets.stream().noneMatch(own::contains), "Ни одна клетка своего столба не попала в цели");

        BlockCoord highest = ClearScaffold.highest(targets);
        BlockCoord feet = new BlockCoord(0, -3, 0);
        ClearScaffold.Decision decision = ClearScaffold.decide(feet, highest, MAX_STAND_RISE, false);
        assertEquals(
                ClearScaffold.Action.CLIMB,
                decision.action(),
                "Верх высокой колонки не достать с земли: без подъёма клетки объявлялись недостижимыми "
                        + "(«к клетке ... y=97 нет подхода» в живом журнале)");
        assertEquals(
                highest.y() - feet.y() - MAX_STAND_RISE,
                decision.height(),
                "Подъём считается от верхней цели, а не от низа ящика: иначе дух встаёт на подмости и снова "
                        + "не достаёт цель");
    }
}
