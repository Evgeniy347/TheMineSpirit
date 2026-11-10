package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RouteTrailCodec {
    private RouteTrailCodec() {}

    public static int[] encode(RouteTrail trail) {
        Objects.requireNonNull(trail, "Тропа не может быть null: кодировать нечего");
        int[] packed = new int[trail.cells().size() * 3];
        int index = 0;
        for (BlockCoord cell : trail.cells()) {
            packed[index++] = cell.x();
            packed[index++] = cell.y();
            packed[index++] = cell.z();
        }
        return packed;
    }

    public static Optional<RouteTrail> decode(int[] packed, String block) {
        Objects.requireNonNull(packed, "Числа клеток не могут быть null: битые данные не читают молча");
        Objects.requireNonNull(block, "Материал тропы не может быть null: его берут из сохранения");
        if (packed.length == 0 || packed.length % 3 != 0) {
            return Optional.empty();
        }
        List<BlockCoord> cells = new ArrayList<>(packed.length / 3);
        for (int index = 0; index < packed.length; index += 3) {
            cells.add(new BlockCoord(packed[index], packed[index + 1], packed[index + 2]));
        }
        return Optional.of(new RouteTrail(cells, block));
    }
}
