package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FarmTileTests {
    static Stream<Arguments> constructorRows() {
        return Stream.of(
                Arguments.of(Ground.FARMLAND, Above.AIR, Optional.empty(), true,
                        "пустая грядка принимает посадку"),
                Arguments.of(Ground.DIRT, Above.AIR, Optional.empty(), true,
                        "голая земля с воздухом ждёт вспашки"),
                Arguments.of(Ground.GRASS, Above.WEED, Optional.empty(), true,
                        "сорная трава над дёрном срубается перед вспашкой"),
                Arguments.of(Ground.PATH, Above.AIR, Optional.empty(), true,
                        "тропинка пашется как земля"),
                Arguments.of(Ground.OTHER, Above.BLOCKED, Optional.empty(), true,
                        "камень с блоком сверху просто не подходит ни под одну работу"),
                Arguments.of(Ground.FARMLAND, Above.CROP,
                        Optional.of(new CropState(CropKind.WHEAT, 7)), true,
                        "зрелая пшеница на грядке собирается"),
                Arguments.of(Ground.FARMLAND, Above.CROP,
                        Optional.of(new CropState(CropKind.BEETROOT, 1)), true,
                        "растущая свёкла на грядке ждёт костной муки"),
                Arguments.of(Ground.DIRT, Above.CROP,
                        Optional.of(new CropState(CropKind.WHEAT, 7)), true,
                        "инвариант держится по верху CROP независимо от земли"),
                Arguments.of(Ground.FARMLAND, Above.CROP, Optional.empty(), false,
                        "посев без состояния лишает harvest проверки зрелости"),
                Arguments.of(Ground.FARMLAND, Above.AIR,
                        Optional.of(new CropState(CropKind.WHEAT, 7)), false,
                        "посев при воздухе сверху создал бы урожай из ничего"),
                Arguments.of(Ground.DIRT, Above.WEED,
                        Optional.of(new CropState(CropKind.CARROT, 2)), false,
                        "посев при сорной траве смешал бы прополку со сбором"),
                Arguments.of(Ground.OTHER, Above.BLOCKED,
                        Optional.of(new CropState(CropKind.POTATO, 7)), false,
                        "посев под блоком скрыл бы урожай от проверки света"));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("constructorRows")
    void constructor_TileTable_HoldsCropInvariant(Ground ground, Above above, Optional<CropState> crop,
            boolean valid, String reason) {
        if (valid) {
            assertEquals(new FarmTile(ground, above, crop), new FarmTile(ground, above, crop),
                    "Допустимая клетка обязана создаваться (" + reason + "): иначе планировщик не увидит грядку");
            return;
        }

        assertThrows(IllegalArgumentException.class, () -> new FarmTile(ground, above, crop),
                "Нарушение инварианта обязано отвергаться (" + reason + "): иначе планировщик увидит урожай там, "
                        + "где его нет, или пропустит зрелый посев");
    }
}
