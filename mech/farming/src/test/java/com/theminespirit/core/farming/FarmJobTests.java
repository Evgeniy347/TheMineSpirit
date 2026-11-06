package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FarmJobTests {
    static Stream<Arguments> jobTileRows() {
        Optional<CropState> none = Optional.empty();
        return Stream.of(
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.DIRT, Above.AIR, none),
                        CropKind.ANY,
                        true,
                        true,
                        "голая земля с воздухом и водой пашется"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.GRASS, Above.AIR, none),
                        CropKind.ANY,
                        true,
                        true,
                        "дёрн с воздухом и водой пашется"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.PATH, Above.WEED, none),
                        CropKind.ANY,
                        true,
                        true,
                        "сорная трава не мешает вспашке: дух срубает её перед мотыгой"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.GRASS, Above.WEED, none),
                        CropKind.ANY,
                        true,
                        true,
                        "дёрн с сорняком и водой пашется"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.DIRT, Above.AIR, none),
                        CropKind.ANY,
                        false,
                        false,
                        "без воды земля пересохнет и вернётся в землю: вспашка запрещена"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.DIRT, Above.WEED, none),
                        CropKind.ANY,
                        false,
                        false,
                        "сухая земля с сорняком не пашется даже после прополки"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.FARMLAND, Above.AIR, none),
                        CropKind.ANY,
                        true,
                        false,
                        "готовая грядка не перепахивается"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.OTHER, Above.AIR, none),
                        CropKind.ANY,
                        true,
                        false,
                        "камень не пашется"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.DIRT, Above.BLOCKED, none),
                        CropKind.ANY,
                        true,
                        false,
                        "земля под блоком не пашется: сверху нет места ростку"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.DIRT, Above.CROP, wheat(7)),
                        CropKind.WHEAT,
                        true,
                        false,
                        "посев не сносится мотыгой: урожай собирает только harvest"),
                Arguments.of(
                        FarmJob.TILL,
                        tile(Ground.PATH, Above.AIR, none),
                        CropKind.ANY,
                        false,
                        false,
                        "сухая тропинка ждёт воды"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.FARMLAND, Above.AIR, none),
                        CropKind.WHEAT,
                        true,
                        true,
                        "пустая грядка принимает пшеницу"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.FARMLAND, Above.AIR, none),
                        CropKind.WHEAT,
                        false,
                        true,
                        "посадке вода не нужна: увлажнение требуется только вспашке"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.FARMLAND, Above.AIR, none),
                        CropKind.ANY,
                        true,
                        true,
                        "фильтр не ограничивает посадку: вид задаёт harvested-пересадка и план"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.FARMLAND, Above.WEED, none),
                        CropKind.WHEAT,
                        true,
                        false,
                        "сорная трава мешает посадке: сначала прополка и вспашка"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.FARMLAND, Above.CROP, wheat(3)),
                        CropKind.WHEAT,
                        true,
                        false,
                        "занятая грядка не засаживается повторно"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.DIRT, Above.AIR, none),
                        CropKind.WHEAT,
                        true,
                        false,
                        "невспаханная земля не принимает семена"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.FARMLAND, Above.BLOCKED, none),
                        CropKind.WHEAT,
                        true,
                        false,
                        "грядка под блоком не засаживается: ростку некуда расти"),
                Arguments.of(
                        FarmJob.PLANT,
                        tile(Ground.GRASS, Above.AIR, none),
                        CropKind.CARROT,
                        true,
                        false,
                        "дёрн сначала пашется, семена только в грядку"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, wheat(7)),
                        CropKind.WHEAT,
                        true,
                        true,
                        "зрелая пшеница собирается своим видом"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, wheat(7)),
                        CropKind.ANY,
                        true,
                        true,
                        "any собирает любой зрелый посев"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, wheat(3)),
                        CropKind.WHEAT,
                        true,
                        false,
                        "пшеница age 3 недозрела: harvest её не трогает"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, wheat(3)),
                        CropKind.ANY,
                        true,
                        false,
                        "any не собирает растущий посев"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, beet(3)),
                        CropKind.BEETROOT,
                        true,
                        true,
                        "свёкла зреет на age 3: граница зрелости ниже пшеницы"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, beet(2)),
                        CropKind.BEETROOT,
                        true,
                        false,
                        "свёкла age 2 ещё растёт"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, beet(3)),
                        CropKind.ANY,
                        true,
                        true,
                        "any видит зрелую свёклу"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, wheat(7)),
                        CropKind.CARROT,
                        true,
                        false,
                        "пшеница не собирается фильтром моркови"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, carrot(7)),
                        CropKind.WHEAT,
                        true,
                        false,
                        "морковь не собирается фильтром пшеницы"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.AIR, none),
                        CropKind.ANY,
                        true,
                        false,
                        "пустая грядка не собирается"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.DIRT, Above.CROP, wheat(7)),
                        CropKind.WHEAT,
                        true,
                        true,
                        "урожай собирается независимо от земли под ним"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, potato(7)),
                        CropKind.POTATO,
                        true,
                        true,
                        "зрелый картофель собирается"),
                Arguments.of(
                        FarmJob.HARVEST,
                        tile(Ground.FARMLAND, Above.CROP, wheat(7)),
                        CropKind.BEETROOT,
                        true,
                        false,
                        "пшеница не собирается фильтром свёклы"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, wheat(3)),
                        CropKind.WHEAT,
                        true,
                        true,
                        "растущая пшеница принимает костную муку"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, wheat(3)),
                        CropKind.ANY,
                        true,
                        true,
                        "any подкармливает любой растущий посев"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, wheat(7)),
                        CropKind.WHEAT,
                        true,
                        false,
                        "зрелая пшеница муку не тратит"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, wheat(7)),
                        CropKind.ANY,
                        true,
                        false,
                        "any не тратит муку на зрелый посев"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, beet(3)),
                        CropKind.BEETROOT,
                        true,
                        false,
                        "зрелая свёкла age 3 муку не тратит"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, beet(2)),
                        CropKind.BEETROOT,
                        true,
                        true,
                        "растущая свёкла age 2 принимает муку"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, wheat(3)),
                        CropKind.CARROT,
                        true,
                        false,
                        "пшеница не подкармливается фильтром моркови"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.AIR, none),
                        CropKind.ANY,
                        true,
                        false,
                        "пустая грядка не подкармливается"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.WEED, none),
                        CropKind.ANY,
                        true,
                        false,
                        "сорняк не подкармливается"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.DIRT, Above.CROP, carrot(5)),
                        CropKind.CARROT,
                        true,
                        true,
                        "подкормка не зависит от земли под посевом"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, potato(7)),
                        CropKind.POTATO,
                        true,
                        false,
                        "зрелый картофель муку не тратит"),
                Arguments.of(
                        FarmJob.BONE_MEAL,
                        tile(Ground.FARMLAND, Above.CROP, potato(6)),
                        CropKind.ANY,
                        true,
                        true,
                        "картофель age 6 ещё растёт и принимает муку"));
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("jobTileRows")
    void eligible_JobTileTable_MatchesContract(
            FarmJob job, FarmTile tile, CropKind filter, boolean watered, boolean expected, String reason) {
        assertEquals(
                expected,
                job.eligible(tile, filter, watered),
                "Клетка обязана " + (expected ? "подходить" : "не подходить") + " под " + job + " (" + reason + "): "
                        + "иначе планировщик вспашет сухую землю, посадит в сорняк или соберёт недозрелое");
    }

    private static FarmTile tile(Ground ground, Above above, Optional<CropState> crop) {
        return new FarmTile(ground, above, crop);
    }

    private static Optional<CropState> wheat(int age) {
        return Optional.of(new CropState(CropKind.WHEAT, age));
    }

    private static Optional<CropState> carrot(int age) {
        return Optional.of(new CropState(CropKind.CARROT, age));
    }

    private static Optional<CropState> potato(int age) {
        return Optional.of(new CropState(CropKind.POTATO, age));
    }

    private static Optional<CropState> beet(int age) {
        return Optional.of(new CropState(CropKind.BEETROOT, age));
    }
}
