package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class More3StoreTests {
    @Test
    void migrateColumns_OldDangerAtFeet_MovesToBaseKeepingColumnAndCounters() {
        More3Store store = new More3Store();
        store.noteDamage(new BlockCoord(321, 80, -654), "mob", 100);

        store.migrateColumns(70);

        More3Store.Danger danger = store.recentDangers(100).get(0);
        assertEquals(
                new BlockCoord(321, 70, -654),
                danger.position(),
                "Старая запись хранила ноги духа (крыша склада y=80); основание опасности — низ колонки "
                        + "(уровень ограды y=70). Иначе avoidDanger и будущие потребители видят произвольную y"
                        + " При смене migrateColumns_OldDangerAtFeet_MovesToBaseKeepingColumnAndCounters проверь это утверждение.");
        assertEquals(
                "mob",
                danger.kind(),
                "Миграция не трогает вид опасности"
                        + " Иначе тест migrateColumns_OldDangerAtFeet_MovesToBaseKeepingColumnAndCounters перестанет ловить эту поломку. При смене migrateColumns_OldDangerAtFeet_MovesToBaseKeepingColumnAndCounters проверь это утверждение.");
        assertEquals(
                1,
                danger.hits(),
                "Миграция не сбрасывает счётчик ударов"
                        + " Иначе тест migrateColumns_OldDangerAtFeet_MovesToBaseKeepingColumnAndCounters перестанет ловить эту поломку. При смене migrateColumns_OldDangerAtFeet_MovesToBaseKeepingColumnAndCounters проверь это утверждение.");
    }

    @Test
    void migrateColumns_RunsOnce_MigratedRecordIsNotRewrittenLater() {
        More3Store store = new More3Store();
        store.noteDamage(new BlockCoord(1, 80, 2), "mob", 100);
        store.migrateColumns(70);

        store.noteDamage(new BlockCoord(3, 80, 4), "mob", 110);
        store.migrateColumns(90);

        List<BlockCoord> positions = store.recentDangers(110).stream()
                .map(More3Store.Danger::position)
                .toList();
        assertEquals(
                List.of(new BlockCoord(1, 70, 2), new BlockCoord(3, 80, 4)),
                positions,
                "Признак миграции один на хранилище: уже выправленные записи не переезжают повторно на новую "
                        + "высоту, а свежая запись живёт со своим основанием"
                        + " Иначе тест migrateColumns_RunsOnce_MigratedRecordIsNotRewrittenLater перестанет ловить эту поломку. При смене migrateColumns_RunsOnce_MigratedRecordIsNotRewrittenLater проверь это утверждение.");
    }

    @Test
    void load_OldSavedRecordWithoutBase_MigratesAfterLoad() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("Stage", 1);
        saved.putLong("StageStart", 5);
        ListTag dangers = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt("X", 10);
        entry.putInt("Y", 80);
        entry.putInt("Z", -20);
        entry.putString("Kind", "mob");
        entry.putInt("Hits", 2);
        entry.putInt("Deaths", 1);
        entry.putLong("Last", 100);
        dangers.add(entry);
        saved.put("Dangers", dangers);

        More3Store loaded = More3Store.load(saved, null);
        loaded.migrateColumns(70);

        assertEquals(
                new BlockCoord(10, 70, -20),
                loaded.recentDangers(100).get(0).position(),
                "Записи из старого сейва до правки обязаны мигрировать при первой загрузке: иначе после "
                        + "перезапуска они снова дают крышу/столб вместо основания колонки"
                        + " При смене load_OldSavedRecordWithoutBase_MigratesAfterLoad проверь это утверждение.");
    }

    @Test
    void saveLoad_DangerBase_SurvivesRestart() {
        More3Store store = new More3Store();
        store.noteDamage(new BlockCoord(4, 70, 5), "mob", 100);

        CompoundTag saved = store.save(new CompoundTag(), RegistryAccess.EMPTY);
        More3Store loaded = More3Store.load(saved, null);

        assertEquals(
                new BlockCoord(4, 70, 5),
                loaded.recentDangers(100).get(0).position(),
                "Основание опасности обязано пережить перезапуск: иначе миграция повторится и снова сдвинет y"
                        + " При смене saveLoad_DangerBase_SurvivesRestart проверь это утверждение.");
        assertEquals(
                Tag.TAG_COMPOUND,
                ((ListTag) saved.get("Dangers")).getElementType(),
                "Опасности сохраняются списком compound: формат старого сейва остаётся читаемым"
                        + " Иначе тест saveLoad_DangerBase_SurvivesRestart перестанет ловить эту поломку. При смене saveLoad_DangerBase_SurvivesRestart проверь это утверждение.");
    }

    @Test
    void noteDeath_DropStaysAtFeet_DangerGetsColumnBase() {
        More3Store store = new More3Store();

        store.noteDeath(new BlockCoord(7, 70, 8), new BlockCoord(7, 80, 8), 100);

        assertEquals(
                new BlockCoord(7, 70, 8),
                store.recentDangers(100).get(0).position(),
                "Опасность от смерти — основание колонки, а не крыша, где дух умер"
                        + " Иначе тест noteDeath_DropStaysAtFeet_DangerGetsColumnBase перестанет ловить эту поломку. При смене noteDeath_DropStaysAtFeet_DangerGetsColumnBase проверь это утверждение.");
        assertEquals(
                new BlockCoord(7, 80, 8),
                store.activeDrops(100).get(0).position(),
                "Дроп после смерти остаётся там, где дух умер: подбирать его идут к фактической клетке, а не к "
                        + "уровню ограды"
                        + " Иначе тест noteDeath_DropStaysAtFeet_DangerGetsColumnBase перестанет ловить эту поломку. При смене noteDeath_DropStaysAtFeet_DangerGetsColumnBase проверь это утверждение.");
        assertNotEquals(
                store.recentDangers(100).get(0).hits(),
                0,
                "Смерть учитывается в счётчике опасного места"
                        + " Иначе тест noteDeath_DropStaysAtFeet_DangerGetsColumnBase перестанет ловить эту поломку. При смене noteDeath_DropStaysAtFeet_DangerGetsColumnBase проверь это утверждение.");
    }
}
