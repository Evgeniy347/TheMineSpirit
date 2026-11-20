package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.farming.api.CropKind;
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
                        "дёрн сначала пашется, семена только в грядку"));
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("jobTileRows")
    void eligible_JobTileTable_MatchesContract(
            FarmJob job, FarmTile tile, CropKind filter, boolean watered, boolean expected, String reason) {
        assertEquals(
                expected,
                job.eligible(tile, filter, watered),
                "Клетка обязана " + (expected ? "подходить" : "не подходить") + " под " + job + " (" + reason + "): "
                        + "иначе планировщик вспашет сухую землю, посадит в сорняк и цикл фермы встанет. При смене "
                        + "FarmJob.eligible проверь таблицы jobTileRows здесь и в FarmJobHarvestTests");
    }

    private static FarmTile tile(Ground ground, Above above, Optional<CropState> crop) {
        return new FarmTile(ground, above, crop);
    }

    private static Optional<CropState> wheat(int age) {
        return Optional.of(new CropState(CropKind.WHEAT, age));
    }
}
