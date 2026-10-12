package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class CropKindProperties {
    @Property
    void ofBlock_BlockIdOfKind_ReturnsThatKind(@ForAll CropKind kind) {
        if (kind == CropKind.ANY) {
            return;
        }

        assertEquals(Optional.of(kind), CropKind.ofBlock(kind.blockId()),
                "Обратное сопоставление обязано держаться для блока вида " + kind
                        + ": иначе распознавание посева и поиск цели расходятся");
    }

    @Property
    void blockId_EverySpecificKind_MatchesMinecraftIdPattern(@ForAll CropKind kind) {
        if (kind == CropKind.ANY) {
            return;
        }

        assertTrue(kind.blockId().matches("minecraft:[a-z_]+"),
                "id блока обязан идти по шаблону minecraft:[a-z_]+: LevelFarmView ищет вид по id из реестра, "
                        + "а заглавные буквы или дефис сломают сопоставление. Фактический: " + kind.blockId());
    }

    @Property
    void cropState_AgeInsideRange_MaturityMatchesMaxAge(@ForAll CropKind kind) {
        if (kind == CropKind.ANY) {
            return;
        }

        for (int age = 0; age <= kind.maxAge(); age++) {
            assertEquals(age == kind.maxAge(), new CropState(kind, age).isMature(),
                    "Зрелость обязана наступать ровно на максимальном возрасте вида " + kind
                            + ": иначе harvest соберёт недозрелое или пропустит зрелое. Возраст: " + age);
        }
    }
}
