package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StandChoiceTests {
    private static final BlockCoord TARGET = new BlockCoord(0, 0, 0);
    private static final BlockCoord SPIRIT = new BlockCoord(5, 1, 0);

    @Test
    void choose_OpenGround_PicksCheapestReachableStand() {
        CountingSafety world = openGround();

        Optional<BlockCoord> stand = StandChoice.choose(TARGET, SPIRIT, world);

        assertEquals(Optional.of(new BlockCoord(3, 1, 0)), stand,
                "На ровной земле лучшая стоянка — (3, 1, 0): ближайшая к духу из достижимых от цели, "
                        + "иначе дух идёт к дальней стоянке вместо ближней");
    }

    @Test
    void choose_LavaRingAroundBest_PicksNextBest() {
        CountingSafety world = openGround().set(3, 1, 1, Cell.LAVA);

        Optional<BlockCoord> stand = StandChoice.choose(TARGET, SPIRIT, world);

        assertEquals(Optional.of(new BlockCoord(3, 1, -1)), stand,
                "Стоянка (3, 1, 0) рядом с лавой обязана отвергаться кольцом безопасности: дух иначе "
                        + "встанет у лавы и сгорит; следующая дешёвая — (3, 1, -1)");
    }

    @Test
    void choose_SealedTarget_ReturnsEmpty() {
        CountingSafety world = new CountingSafety();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -1; y <= 4; y++) {
                    world.set(x, y, z, Cell.SOLID);
                }
            }
        }

        assertEquals(Optional.empty(), StandChoice.choose(TARGET, SPIRIT, world),
                "Замурованная цель не даёт стоянок: духу подойти некуда, скрипт обязан вернуть FAILED, "
                        + "а не вести духа в стену");
    }

    @Test
    void candidates_TargetColumn_ExcludedAndLevelsMatch() {
        List<BlockCoord> candidates = StandChoice.candidates(TARGET);

        assertEquals(96, candidates.size(),
                "Кандидатов обязано быть 96 = 2 уровня × (7 × 7 − столбец цели): иначе часть стоянок "
                        + "не рассматривается и дух пропускает ближний подход");
        assertTrue(candidates.stream().noneMatch(c -> c.x() == 0 && c.z() == 0),
                "Столбец цели исключён из кандидатов: стоянка внутри цели невозможна");
        assertTrue(candidates.stream().allMatch(c -> c.y() == 1 || c.y() == 2),
                "Кандидаты — только на уровнях ног y+1 и y+2: иначе дух пытается встать внутрь цели или в небо");
    }

    @Test
    void choose_OpenGround_SafetyReadsBounded() {
        CountingSafety world = openGround();

        StandChoice.choose(TARGET, SPIRIT, world);

        assertTrue(world.reads() <= 19 * StandChoice.candidates(TARGET).size(),
                "Выбор стоянки читает не более 19 клеток на кандидата (ноги, голова, пол и два кольца по 8): "
                        + "иначе планирование цели кладёт серверный тик. Чтений: " + world.reads());
    }

    @Test
    void eyeHeight_ValueCheck_EqualsEyeLevel() {
        assertEquals(1.62, StandChoice.EYE_HEIGHT,
                "Высота глаз духа — 1.62: от неё считается досягаемость цели, неверная высота либо "
                        + "отвергнет близкую цель, либо примет недостижимую");
    }

    @Test
    void reachDistance_ValueCheck_EqualsBreakReach() {
        assertEquals(4.5, StandChoice.REACH_DISTANCE,
                "Досягаемость стоянки — 4.5 от глаз до центра цели: как досягаемость ломки, иначе дух "
                        + "встанет там, откуда цель не достать");
    }

    @Test
    void horizontalSpread_ValueCheck_EqualsThree() {
        assertEquals(3, StandChoice.HORIZONTAL_SPREAD,
                "Разлёт кандидатов — 3 клетки по горизонтали: шире — лишние чтения безопасности, "
                        + "уже — пропущенные ближние стоянки");
    }

    private static CountingSafety openGround() {
        return new CountingSafety().fillSolid(0, -5, -5, 5, 5);
    }
}
