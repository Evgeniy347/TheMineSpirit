package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.farming.api.CropKind;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FarmJobHarvestTests {
    static Stream<Arguments> jobTileRows() {
        Optional<CropState> none = Optional.empty();
        return Stream.of(
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
                        + "иначе планировщик соберёт недозрелое или потратит муку на зрелый посев. При смене "
                        + "FarmJob.eligible проверь таблицы jobTileRows здесь и в FarmJobTests");
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
