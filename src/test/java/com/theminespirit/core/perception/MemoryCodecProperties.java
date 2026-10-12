package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MemoryCodecProperties {
    private static final int MAX_POINTS = 16;

    @Provide
    Arbitrary<MemoryStore> stores() {
        Arbitrary<String> suffix = Arbitraries.strings().ofMaxLength(10);
        Arbitrary<BlockCoord> pos = Combinators.combine(Arbitraries.integers(), Arbitraries.integers(),
                Arbitraries.integers()).as(BlockCoord::new);
        Arbitrary<String> dimension = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .map(name -> "mod:" + name);
        Arbitrary<List<MemoryPoint>> points = Combinators.combine(suffix, Arbitraries.of(MemoryType.class), dimension,
                pos).as((tail, type, dim, at) -> new MemoryPoint("p" + tail, type, dim, at)).list()
                .ofMaxSize(MAX_POINTS);
        return points.map(MemoryCodecProperties::withUniqueNames);
    }

    private static MemoryStore withUniqueNames(List<MemoryPoint> points) {
        List<MemoryPoint> unique = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            MemoryPoint point = points.get(i);
            unique.add(new MemoryPoint(i + "_" + point.name(), point.type(), point.dimension(), point.pos()));
        }
        return new MemoryStore(unique);
    }

    @Property
    void roundTrip_AnyStore_DecodeOfEncodeIsEqual(@ForAll("stores") MemoryStore store) {
        assertEquals(store, MemoryCodec.decode(MemoryCodec.encode(store)),
                "decode(encode(store)) обязан равняться store для любой памяти: иначе точки меняются при "
                        + "перезапуске сервера");
    }
}
