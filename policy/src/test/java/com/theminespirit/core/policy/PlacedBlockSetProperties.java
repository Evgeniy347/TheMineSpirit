package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class PlacedBlockSetProperties {
    @Provide
    Arbitrary<BlockCoord> anyCoord() {
        return Combinators.combine(
                        Arbitraries.integers().between(-33_554_432, 33_554_431),
                        Arbitraries.integers().between(-2048, 2047),
                        Arbitraries.integers().between(-33_554_432, 33_554_431))
                .as(BlockCoord::new);
    }

    @Property(tries = 200)
    void unpack_PackedCoordinate_RestoresIt(@ForAll("anyCoord") BlockCoord coord) {
        assertEquals(
                coord,
                PlacedBlockSet.unpack(PlacedBlockSet.pack(coord)),
                "Сжатие позиции в long обратимо на всём допустимом диапазоне, иначе после перезапуска блоки "
                        + "сдвинутся и защита перестанет узнавать свои постройки. При смене упаковки проверь "
                        + "пределы X/Z ±33554431 и Y -2048..2047");
    }

    @Property(tries = 200)
    void contains_RandomAddRemoveBelowLimit_MatchesPlainSet(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        PlacedBlockSet set = new PlacedBlockSet();
        Set<BlockCoord> model = new HashSet<>();
        BlockCoord[] pool = new BlockCoord[60];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new BlockCoord(random.nextInt(80) - 40, random.nextInt(40) - 20, random.nextInt(80) - 40);
        }

        for (int step = 0; step < 300; step++) {
            BlockCoord coord = pool[random.nextInt(pool.length)];
            if (random.nextBoolean()) {
                set.add(coord);
                model.add(coord);
            } else {
                set.remove(coord);
                model.remove(coord);
            }
        }

        for (BlockCoord coord : pool) {
            assertEquals(
                    model.contains(coord),
                    set.contains(coord),
                    "contains истинно ровно для добавленных и не удалённых позиций, иначе защита путает свои и чужие блоки. Позиция: "
                            + coord + ". При смене add/remove проверь повторное удаление и повторное добавление");
        }
        assertEquals(
                model.size(),
                set.size(),
                "Размер набора равен числу добавленных и не удалённых позиций: расхождение выдаёт потерянные "
                        + "или задвоенные записи, иначе защита путает свои блоки с чужими. "
                        + "При смене учёта размера проверь цепочку add/remove на одной позиции");
    }

    @Property(tries = 200)
    void decode_EncodedRandomSet_EqualsOriginal(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 200; i++) {
            set.add(new BlockCoord(random.nextInt(200) - 100, random.nextInt(60) - 30, random.nextInt(200) - 100));
        }

        PlacedBlockSet restored = PlacedBlockCodec.decode(PlacedBlockCodec.encode(set));

        assertEquals(
                set,
                restored,
                "Кодек обратим: набор после сохранения и загрузки равен исходному, иначе после перезапуска "
                        + "защита забывает блоки. При смене формата проверь предельные и отрицательные координаты");
        assertTrue(
                restored.size() == set.size(),
                "Размеры до и после кодека совпадают: иначе загрузка теряет или задваивает блоки. "
                        + "При смене decode проверь размер на наборе с повторами координат");
    }
}
