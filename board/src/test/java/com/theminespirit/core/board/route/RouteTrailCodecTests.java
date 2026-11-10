package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RouteTrailCodecTests {
    @Test
    void encodeThenDecode_RoundTrip_RestoresCellsAndBlock() {
        RouteTrail trail = new RouteTrail(List.of(new BlockCoord(1, 64, 2), new BlockCoord(3, 65, 4)), "sand");

        Optional<RouteTrail> restored = RouteTrailCodec.decode(RouteTrailCodec.encode(trail), "sand");

        assertEquals(
                Optional.of(trail),
                restored,
                "Клетки и материал тропы переживают сохранение мира без изменений: иначе старый след не убрать");
    }

    @Test
    void decode_BrokenCellCount_IsEmpty() {
        assertTrue(
                RouteTrailCodec.decode(new int[] {1, 64}, "dirt").isEmpty(),
                "Битые клетки не превращаются в тропу: снятие по половине координаты сломало бы чужой блок");
    }

    @Test
    void decode_NoCells_IsEmpty() {
        assertTrue(
                RouteTrailCodec.decode(new int[0], "dirt").isEmpty(),
                "Пустой список не выдаётся за тропу: снимать нечего");
    }
}
