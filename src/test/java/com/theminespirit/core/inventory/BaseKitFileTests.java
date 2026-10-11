package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaseKitFileTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @Test
    void baseKitFile_Rules_MentionOnlyCategoriesOfValueTable() {
        List<String> categoryNames = new ArrayList<>();
        for (Category category : VALUES.categories()) {
            categoryNames.add(category.name());
        }
        for (KitRule rule : KIT.rules()) {
            if (rule.category() == null) {
                continue;
            }
            assertTrue(categoryNames.contains(rule.category()),
                    "Категория '" + rule.category() + "' из файла базового набора отсутствует в файле "
                            + "ценностей: правило с несуществующей категорией молча не описывает ничего, "
                            + "и разгрузка отдала бы в контейнер весь запас еды. Категории файла ценностей: "
                            + categoryNames);
        }
    }

    @Test
    void baseKitFile_Loaded_KeepsToolsFoodAndSupport() {
        assertTrue(KIT.rules().stream().anyMatch(rule -> rule.mode() == KitMode.BEST && rule.keep() == 1
                        && !rule.match().isEmpty() && rule.match().size() == 1 && rule.match().get(0).endsWith("hoe")),
                "В базовом наборе обязана быть тяпка (режим best, квота 1): без тяпки дух не соберёт "
                        + "урожай на базе и пойдёт искать инструмент вместо дела");
        assertTrue(KIT.rules().stream().anyMatch(rule -> "food".equals(rule.category())),
                "В базовом наборе обязана быть еда: без еды дух голодает в походе");
    }

    @Test
    void baseKitFile_Rules_AnyModeKeepsPositiveQuota() {
        assertEquals(true, KIT.rules().stream().noneMatch(rule -> rule.mode() == KitMode.ANY && rule.keep() == 0),
                "Ни одно правило режима any не имеет квоты 0: квота 0 означает «ничего не оставляем», "
                        + "и группа предметов уходит в контейнер целиком, хотя набор должен был её хранить");
    }
}
