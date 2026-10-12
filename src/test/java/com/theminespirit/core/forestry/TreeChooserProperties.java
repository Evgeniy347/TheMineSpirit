package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class TreeChooserProperties {
    @Property(tries = 200)
    void next_AnyLogsAndKnownSet_ChoosesMinimumDistanceAmongUnknown(@ForAll long seed) {
        Random random = new Random(seed);
        List<BlockCoord> logs = randomLogs(random);
        Set<BlockCoord> known = randomKnown(random, logs);

        Optional<BlockCoord> chosen = TreeChooser.next(0, 0, 0, logs, known);

        List<BlockCoord> candidates = logs.stream().filter(coord -> !known.contains(coord)).toList();
        if (candidates.isEmpty()) {
            assertTrue(chosen.isEmpty(),
                    "При пустом списке допустимых брёвен выбирать нечего: пустой результат остановит рубку");
            return;
        }
        assertTrue(chosen.isPresent(),
                "При наличии хотя бы одного допустимого бревна выбор обязан вернуть его: без этого "
                        + "дух останавливается с деревьями вокруг");
        assertTrue(candidates.contains(chosen.orElseThrow()),
                "Выбранное бревно обязано принадлежать допустимым: известное бревно не должно выбираться повторно");
        long minimum = candidates.stream().mapToLong(coord -> distanceSquared(coord)).min().orElseThrow();
        assertEquals(minimum, distanceSquared(chosen.orElseThrow()),
                "Выбор обязан давать бревно с минимальным расстоянием среди допустимых: иначе дух идёт "
                        + "к дальнему дереву, минуя ближнее");
    }

    @Property(tries = 200)
    void next_AnyLogsInAnyOrder_ResultIsStable(@ForAll long seed) {
        Random random = new Random(seed);
        List<BlockCoord> logs = randomLogs(random);

        Optional<BlockCoord> direct = TreeChooser.next(0, 0, 0, logs, Set.of());
        List<BlockCoord> shuffled = new ArrayList<>(logs);
        Collections.shuffle(shuffled, random);
        Optional<BlockCoord> shuffledResult = TreeChooser.next(0, 0, 0, shuffled, Set.of());

        assertEquals(direct, shuffledResult,
                "Порядок списка брёвен не должен влиять на выбор: иначе одна и та же находка скана "
                        + "рубится в разном порядке от вызова к вызову");
    }

    private static List<BlockCoord> randomLogs(Random random) {
        Set<BlockCoord> unique = new LinkedHashSet<>();
        unique.add(new BlockCoord(0, 0, 1));
        int count = 1 + random.nextInt(15);
        while (unique.size() < count) {
            unique.add(new BlockCoord(random.nextInt(7) - 3, random.nextInt(7) - 3, random.nextInt(7) - 3));
        }
        return List.copyOf(unique);
    }

    private static Set<BlockCoord> randomKnown(Random random, List<BlockCoord> logs) {
        Set<BlockCoord> known = new LinkedHashSet<>();
        for (BlockCoord log : logs) {
            if (random.nextBoolean()) {
                known.add(log);
            }
        }
        return known;
    }

    private static long distanceSquared(BlockCoord coord) {
        long dx = (long) coord.x();
        long dy = (long) coord.y();
        long dz = (long) coord.z();
        return dx * dx + dy * dy + dz * dz;
    }
}