package com.theminespirit.core.movement.network;

import static com.theminespirit.core.movement.network.GridWorld.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.movement.BodyStep;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NetworkBuilderTests {
    private static final BuildBox HOUSE = BuildBox.of(-1, 0, -1, 20, 8, 5);
    private static final BlockCoord DOOR = cell(0, 1, 2);

    @Test
    void chains_TwoFloorsWithStairs_ReachFarthestCellOfEachFloor() {
        GridWorld world = GridWorld.twoFloors();
        List<List<BlockCoord>> chains = NetworkBuilder.chains(List.of(DOOR), HOUSE, world);
        Set<BlockCoord> cells = new HashSet<>();
        chains.forEach(cells::addAll);
        assertTrue(
                cells.contains(DOOR),
                "сеть начинается у двери: без неё дух с улицы не войдёт в сеть дома; клетки сети " + cells.size());
        assertTrue(
                cells.stream().anyMatch(c -> c.y() == 5 && c.x() <= 1),
                "ветвь сети обязана дойти по лестнице (верх x=13) до дальнего конца второго этажа (x ≤ 1, ноги y=5): "
                        + "иначе дух из "
                        + "дальней комнаты наверху не найдёт вход в сеть в пределах пути игры");
        assertTrue(
                cells.stream().anyMatch(c -> c.y() == 1 && c.x() >= 18),
                "ветвь сети обязана дойти до дальнего конца первого этажа (x ≥ 18, ноги y=1)");
    }

    @Test
    void chains_AnyHouse_SegmentsAreShortAndWalkableBothWays() {
        GridWorld world = GridWorld.twoFloors();
        for (List<BlockCoord> chain : NetworkBuilder.chains(List.of(DOOR), HOUSE, world)) {
            assertTrue(
                    PathSegment.lengthOf(chain, 0, chain.size() - 1) <= PathSegment.MAX_LENGTH,
                    "отрезок длиннее " + PathSegment.MAX_LENGTH + ": поиск пути игры (32) его не пройдёт — " + chain);
            for (int i = 1; i < chain.size(); i++) {
                BlockCoord from = chain.get(i - 1);
                BlockCoord to = chain.get(i);
                assertEquals(
                        to,
                        BodyStep.twoWay(from, to.x() - from.x(), to.z() - from.z(), world),
                        "соседние клетки отрезка — шаг тела в обе стороны: по сети ходят туда и обратно; " + from
                                + " → " + to);
            }
        }
    }

    @Test
    void chains_SecondDoorInsideTree_IsJoinedNotSeparate() {
        GridWorld world = GridWorld.twoFloors();
        BlockCoord backDoor = cell(19, 1, 2);
        List<List<BlockCoord>> chains = NetworkBuilder.chains(List.of(DOOR, backDoor), HOUSE, world);
        PathNetwork network = new PathNetwork();
        chains.forEach(network::add);
        assertTrue(
                network.route(DOOR, backDoor).isPresent(),
                "две двери одного дома связаны сетью: вторая дверь — ветвь того же дерева, а не отдельный кусок");
    }

    @Test
    void cut_LongCorridor_PiecesShareEndsAndFitMaxLength() {
        List<BlockCoord> corridor = new ArrayList<>();
        for (int x = 0; x <= 70; x++) {
            corridor.add(cell(x, 1, 0));
        }
        List<List<BlockCoord>> pieces = NetworkBuilder.cut(corridor);
        int expected = (int) Math.ceil(70 / PathSegment.MAX_LENGTH);
        assertEquals(
                expected,
                pieces.size(),
                "коридор 70 шагов при пределе " + PathSegment.MAX_LENGTH + " режется на " + expected + " отрезков: "
                        + pieces.size());
        for (int i = 1; i < pieces.size(); i++) {
            List<BlockCoord> previous = pieces.get(i - 1);
            assertEquals(
                    previous.get(previous.size() - 1),
                    pieces.get(i).get(0),
                    "соседние отрезки разреза делят конец: иначе граф сети рвётся в месте разреза");
        }
    }
}
