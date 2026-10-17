package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.ContentSource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableDefinition;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableRegistry;
import com.theminespirit.core.reload.TableStatus;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntellectTuningTests {
    private static final String VALID = """
            {
              "demandIntervalTicks": 100,
              "minSwitchTicks": 200,
              "failBackoffTicks": 200,
              "hysteresisMargin": 0.15,
              "crowdPenalty": 0.2,
              "maxDemand": 10.0,
              "fleeMargin": 1.0,
              "maxFighters": 3,
              "foodDays": 1.0,
              "foodRation": 1.5,
              "foodChildRation": 0.75,
              "foodDemandMin": 4.0,
              "foodDemandMax": 9.0,
              "forageWithdrawCount": 1,
              "forageWithdrawRadius": 8,
              "forageHuntRadius": 16,
              "forageHuntCount": 1,
              "forageFishRadius": 16,
              "forageFishCount": 1,
              "forageFishDuration": 1200,
              "oreTarget": 64,
              "ironTarget": 32,
              "weaponTarget": 2,
              "teamMin": 2,
              "teamMax": 3,
              "inviteTtlTicks": 3600,
              "inviteRepeatTicks": 600,
              "inviteExtendTicks": 1200,
              "inviteMaxExtends": 3,
              "strategicTtlTicks": 6000,
              "healFraction": 0.35,
              "armedDps": 6.0
            }
            """;

    @Test
    void parse_ValidJson_MatchesDefaults() {
        assertEquals(IntellectTuning.defaults(), IntellectTuning.parse(VALID, "intellect.json"),
                "Файл данных обязан совпадать с умолчаниями: иначе значения по умолчанию в коде и в таблице "
                        + "разойдутся, и /reload покажет не те числа, что работали до него");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(IntellectTuning.defaults(), IntellectTuning.load(),
                "Файл data/theminespirit/tactics/intellect.json обязан читаться и совпадать с умолчаниями: "
                        + "коэффициенты интеллекта — данные, а не константы кода");
    }

    @Test
    void defaults_DeclaredValues_MatchTuningLiterals() {
        IntellectTuning tuning = IntellectTuning.defaults();
        assertEquals(100, tuning.demandIntervalTicks(),
                "Пересборка спроса раз в 100 тиков: чаще — лишние чтения запасов, реже — поздно видна нужда");
        assertEquals(200, tuning.minSwitchTicks(),
                "Окно удержания 200 тиков: дольше — дух застревает на устаревшем занятии, короче — дёрганье");
        assertEquals(200, tuning.failBackoffTicks(),
                "Откат провала 200 тиков: короче — дух долбит закрытую дверь, дольше — простаивает без дела");
        assertEquals(0.15, tuning.hysteresisMargin(),
                "Порог переключения 0.15 спроса: меньше — задачи дёргаются, больше — дух игнорирует нужду");
        assertEquals(0.2, tuning.crowdPenalty(),
                "Штраф за толпу 0.2 на духа: мягко разводит духов по деятельностям, не запрещая работу вместе");
        assertEquals(10.0, tuning.maxDemand(),
                "Шкала спроса 0..10: в ней считаются коэффициенты деятельностей и штраф за толпу");
        assertEquals(1.0, tuning.fleeMargin(),
                "Порог отхода: бой при запасе победы не ниже 1 − FLEE_MARGIN, иначе дух гибнет в проигрышном бою");
        assertEquals(3, tuning.maxFighters(),
                "Бойцов не больше трёх: иначе оборона оголяет поселение ради толпы у одного врага");
        assertEquals(1.0, tuning.foodDays(),
                "Норма еды считается на сутки: иначе ферма копит или голодает не в срок");
        assertEquals(1.5, tuning.foodRation(),
                "Взрослому 1.5 сытности в сутки: иначе паёк разъедется с ванильной шкалой голода");
        assertEquals(0.75, tuning.foodChildRation(),
                "Ребёнок ест половину пайка взрослого: иначе норма поселения завышена и ферма копит лишнее");
        assertEquals(4.0, tuning.foodDemandMin(),
                "При любом дефиците добыча еды выше рубки (3): минимум 4 не даёт голоду проиграть лесу");
        assertEquals(9.0, tuning.foodDemandMax(),
                "Пустой склад поднимает добычу еды до 9: выше рубки, но ниже обороны 10");
        assertEquals(64, tuning.oreTarget(), "Цель руды 64: иначе добыча остановится раньше стака");
        assertEquals(32, tuning.ironTarget(), "Цель железа 32: иначе не хватит на инструменты и броню");
        assertEquals(2, tuning.weaponTarget(), "Цель оружия 2: иначе экспедиция ходит без охраны");
        assertEquals(2, tuning.teamMin(), "Экспедиция минимум двое: в одиночку в шахту не ходят");
        assertEquals(3, tuning.teamMax(), "Экспедиция максимум трое: иначе шахта превратится в толпу");
        assertEquals(3600, tuning.inviteTtlTicks(),
                "Вопрос висит 3 минуты: короче — игрок не успеет, дольше — духи стоят без дела");
        assertEquals(600, tuning.inviteRepeatTicks(),
                "Повтор каждые 30 секунд: чаще — спам, реже — игрок забудет про вопрос");
        assertEquals(1200, tuning.inviteExtendTicks(),
                "Продление на 60 секунд: иначе собирающийся игрок не успеет");
        assertEquals(3, tuning.inviteMaxExtends(),
                "Продлений не больше трёх: иначе вопрос висит бесконечно");
        assertEquals(6000, tuning.strategicTtlTicks(),
                "Задание стратегического интеллекта живёт 5 минут: покрывает его цикл 1–5 минут с запасом");
        assertEquals(0.35, tuning.healFraction(),
                "Лечение ниже 0.35 здоровья: иначе дух дерётся до смерти вместо отхода за лечением");
        assertEquals(6.0, tuning.armedDps(),
                "Вооружённый дух бьёт на 6: иначе оценка сил недооценит защитника с мечом");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(DataTables.INSTANCE.intellect().current(), IntellectTuning.loadDefault(),
                "Потребитель обязан брать версию из держателя, а не читать classpath/константу: иначе /reload "
                        + "не поменяет ему числа, хотя таблица перечитана");
    }

    @Test
    void replace_LiveTable_TaskBackoffUsesNewWindow() {
        TableHolder<IntellectTuning> holder = DataTables.INSTANCE.intellect();
        IntellectTuning original = holder.current();
        String changed = VALID.replace("\"failBackoffTicks\": 200", "\"failBackoffTicks\": 1000");
        try {
            holder.replace(IntellectTuning.parse(changed, "test datapack"), TableStatus.Source.DATAPACK,
                    "test datapack", "test-hash", Clock.systemUTC());

            TaskBackoff backoff = new TaskBackoff();
            UUID spirit = UUID.randomUUID();
            backoff.noteFailure(spirit, "harvest_crop", 0);

            assertTrue(backoff.blocked(spirit, "harvest_crop", 500),
                    "Потребитель обязан читать окно отката из держателя на каждом вызове: иначе подмена таблицы "
                            + "видна только /spirit tables, а дух продолжает работать по старым числам");
        } finally {
            holder.replace(original, TableStatus.Source.MOD, "classpath (мод)", "restore", Clock.systemUTC());
        }
    }

    @Test
    void reload_BrokenJson_KeepsPreviousVersion() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<IntellectTuning> holder = registry.registerLazy(registeredDefinition(),
                IntellectTuning::defaults);
        String changed = VALID.replace("\"failBackoffTicks\": 200", "\"failBackoffTicks\": 1000");
        registry.reload(ContentSource.of(Map.of(IntellectTuning.RESOURCE,
                new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK))));

        ContentSource broken = ContentSource.of(Map.of(IntellectTuning.RESOURCE,
                new LoadedContent("{ \"failBackoffTicks\": 10 ", "broken datapack", TableStatus.Source.DATAPACK)));
        TableRegistry.Report report = registry.reload(broken).stream()
                .filter(entry -> entry.id().equals("intellect")).findFirst().orElseThrow();

        assertTrue(report.failed(), "Битый json обязан попасть в отчёт с ошибкой: иначе /reload молча оставит "
                + "игрока без объяснения, почему число не поменялось");
        assertEquals(1000, holder.current().failBackoffTicks(),
                "Битый json не подменяет таблицу пустотой: прежняя версия (1000) остаётся в силе, иначе дух "
                        + "начнёт работать по умолчаниям вместо последней удачной загрузки");
    }

    @Test
    void parse_UnknownField_Throws() {
        String json = VALID.replace("\"armedDps\": 6.0", "\"armedDps\": 6.0, \"вкус\": 1");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> IntellectTuning.parse(json, "intellect.json"),
                "Неизвестное поле ломает загрузку: опечатка в данных не должна молча ложиться в умолчания");

        assertTrue(error.getMessage().contains("вкус"), "Сообщение называет лишнее поле: " + error.getMessage());
    }

    @Test
    void parse_ZeroInterval_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"demandIntervalTicks\": 100",
                        "\"demandIntervalTicks\": 0"), "intellect.json"),
                "Нулевой интервал пересборки отвергается: спрос пересчитывался бы каждый тик");

        assertTrue(error.getMessage().contains("demandIntervalTicks"),
                "Сообщение называет поле: " + error.getMessage());
    }

    @Test
    void parse_NonPositiveMaxDemand_Throws() {
        assertThrows(IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"maxDemand\": 10.0", "\"maxDemand\": 0"),
                        "intellect.json"),
                "Нулевая шкала спроса отвергается: любая деятельность вышла бы за максимум");
    }

    @Test
    void parse_HealFractionAtOne_Throws() {
        assertThrows(IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"healFraction\": 0.35", "\"healFraction\": 1.0"),
                        "intellect.json"),
                "Порог лечения 1.0 отвергается: дух уходил бы лечиться при полном здоровье и не дрался бы вовсе");
    }

    @Test
    void parse_TeamMaxBelowMin_Throws() {
        assertThrows(IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"teamMax\": 3", "\"teamMax\": 1"),
                        "intellect.json"),
                "Максимум команды ниже минимума отвергается: экспедицию нельзя собрать ни при каком составе");
    }

    @Test
    void parse_FoodDemandAboveScale_Throws() {
        assertThrows(IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"foodDemandMax\": 9.0", "\"foodDemandMax\": 20.0"),
                        "intellect.json"),
                "Спрос еды выше шкалы MAX_DEMAND отвергается: распределение сравнивало бы значения вне шкалы");
    }

    @Test
    void parse_NegativeCount_Throws() {
        assertThrows(IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"forageFishCount\": 1", "\"forageFishCount\": 0"),
                        "intellect.json"),
                "Нулевой счётчик добычи отвергается: задача с count 0 недостижима");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<IntellectTuning> registeredDefinition() {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if (definition.id().equals("intellect")) {
                return (TableDefinition<IntellectTuning>) definition;
            }
        }
        throw new AssertionError("таблица intellect не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
