package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntellectTuningTests {
    private static final String VALID = """
            {
              "demandIntervalTicks": 100,
              "minSwitchTicks": 200,
              "failBackoffTicks": 200,
              "hysteresisMargin": 0.15,
              "crowdPenalty": 0.2,
              "workCrowdPenalty": 8.0,
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
              "expeditionCooldownTicks": 3600,
              "strategicTtlTicks": 6000,
              "healFraction": 0.35,
              "armedDps": 6.0
            }
            """;

    @Test
    void parse_ValidJson_MatchesDefaults() {
        assertEquals(
                IntellectTuning.defaults(),
                IntellectTuning.parse(VALID, "intellect.json"),
                "Файл данных обязан совпадать с умолчаниями: иначе значения по умолчанию в коде и в таблице "
                        + "разойдутся, и /reload покажет не те числа, что работали до него"
                        + " При смене parse_ValidJson_MatchesDefaults проверь это утверждение.");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(
                IntellectTuning.defaults(),
                IntellectTuning.load(),
                "Файл data/theminespirit/tactics/intellect.json обязан читаться и совпадать с умолчаниями: "
                        + "коэффициенты интеллекта — данные, а не константы кода"
                        + " Иначе тест load_ClasspathResource_MatchesDefaults перестанет ловить эту поломку. При смене load_ClasspathResource_MatchesDefaults проверь это утверждение.");
    }

    @Test
    void defaults_DeclaredValues_MatchTuningLiterals() {
        IntellectTuning tuning = IntellectTuning.defaults();
        assertEquals(
                100,
                tuning.demandIntervalTicks(),
                "Пересборка спроса раз в 100 тиков: чаще — лишние чтения запасов, реже — поздно видна нужда"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                200,
                tuning.minSwitchTicks(),
                "Окно удержания 200 тиков: дольше — дух застревает на устаревшем занятии, короче — дёрганье"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                200,
                tuning.failBackoffTicks(),
                "Откат провала 200 тиков: короче — дух долбит закрытую дверь, дольше — простаивает без дела"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                0.15,
                tuning.hysteresisMargin(),
                "Порог переключения 0.15 спроса: меньше — задачи дёргаются, больше — дух игнорирует нужду"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                0.2,
                tuning.crowdPenalty(),
                "Штраф за толпу 0.2 на духа: мягко разводит духов по деятельностям, не запрещая работу вместе"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                8.0,
                tuning.workCrowdPenalty(),
                "Штраф за толпу цели работы 8 блоков на духа: в шкале расстояний разводит духов по деревьям и жилам, "
                        + "а не в шкале спроса деятельностей"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                10.0,
                tuning.maxDemand(),
                "Шкала спроса 0..10: в ней считаются коэффициенты деятельностей и штраф за толпу"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                1.0,
                tuning.fleeMargin(),
                "Порог отхода: бой при запасе победы не ниже 1 − FLEE_MARGIN, иначе дух гибнет в проигрышном бою"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                3,
                tuning.maxFighters(),
                "Бойцов не больше трёх: иначе оборона оголяет поселение ради толпы у одного врага"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                1.0,
                tuning.foodDays(),
                "Норма еды считается на сутки: иначе ферма копит или голодает не в срок"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                1.5,
                tuning.foodRation(),
                "Взрослому 1.5 сытности в сутки: иначе паёк разъедется с ванильной шкалой голода"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                0.75,
                tuning.foodChildRation(),
                "Ребёнок ест половину пайка взрослого: иначе норма поселения завышена и ферма копит лишнее"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                4.0,
                tuning.foodDemandMin(),
                "При любом дефиците добыча еды выше рубки (3): минимум 4 не даёт голоду проиграть лесу"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                9.0,
                tuning.foodDemandMax(),
                "Пустой склад поднимает добычу еды до 9: выше рубки, но ниже обороны 10"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                64,
                tuning.oreTarget(),
                "Цель руды 64: иначе добыча остановится раньше стака"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                32,
                tuning.ironTarget(),
                "Цель железа 32: иначе не хватит на инструменты и броню"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                2,
                tuning.weaponTarget(),
                "Цель оружия 2: иначе экспедиция ходит без охраны"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                2,
                tuning.teamMin(),
                "Экспедиция минимум двое: в одиночку в шахту не ходят"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                3,
                tuning.teamMax(),
                "Экспедиция максимум трое: иначе шахта превратится в толпу"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                3600,
                tuning.expeditionCooldownTicks(),
                "Пауза между походами 3 минуты: короче — команда уходит в шахту без передышки, "
                        + "дольше — поселение простаивает без руды"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                6000,
                tuning.strategicTtlTicks(),
                "Задание стратегического интеллекта живёт 5 минут: покрывает его цикл 1–5 минут с запасом"
                        + " Иначе тест defaults_DeclaredValues_MatchTuningLiterals перестанет ловить эту поломку. При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                0.35,
                tuning.healFraction(),
                "Лечение ниже 0.35 здоровья: иначе дух дерётся до смерти вместо отхода за лечением"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
        assertEquals(
                6.0,
                tuning.armedDps(),
                "Вооружённый дух бьёт на 6: иначе оценка сил недооценит защитника с мечом"
                        + " При смене defaults_DeclaredValues_MatchTuningLiterals проверь это утверждение.");
    }

    @Test
    void parse_UnknownField_Throws() {
        String json = VALID.replace("\"armedDps\": 6.0", "\"armedDps\": 6.0, \"вкус\": 1");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> IntellectTuning.parse(json, "intellect.json"),
                "Неизвестное поле ломает загрузку: опечатка в данных не должна молча ложиться в умолчания"
                        + " При смене parse_UnknownField_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("вкус"),
                "Сообщение называет лишнее поле: " + error.getMessage()
                        + " Иначе тест parse_UnknownField_Throws перестанет ловить эту поломку. При смене parse_UnknownField_Throws проверь это утверждение.");
    }

    @Test
    void parse_ZeroInterval_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> IntellectTuning.parse(
                        VALID.replace("\"demandIntervalTicks\": 100", "\"demandIntervalTicks\": 0"), "intellect.json"),
                "Нулевой интервал пересборки отвергается: спрос пересчитывался бы каждый тик"
                        + " Иначе тест parse_ZeroInterval_Throws перестанет ловить эту поломку. При смене parse_ZeroInterval_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("demandIntervalTicks"),
                "Сообщение называет поле: " + error.getMessage()
                        + " Иначе тест parse_ZeroInterval_Throws перестанет ловить эту поломку. При смене parse_ZeroInterval_Throws проверь это утверждение.");
    }

    @Test
    void parse_NonPositiveMaxDemand_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"maxDemand\": 10.0", "\"maxDemand\": 0"), "intellect.json"),
                "Нулевая шкала спроса отвергается: любая деятельность вышла бы за максимум"
                        + " Иначе тест parse_NonPositiveMaxDemand_Throws перестанет ловить эту поломку. При смене parse_NonPositiveMaxDemand_Throws проверь это утверждение.");
    }

    @Test
    void parse_HealFractionAtOne_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> IntellectTuning.parse(
                        VALID.replace("\"healFraction\": 0.35", "\"healFraction\": 1.0"), "intellect.json"),
                "Порог лечения 1.0 отвергается: дух уходил бы лечиться при полном здоровье и не дрался бы вовсе"
                        + " Иначе тест parse_HealFractionAtOne_Throws перестанет ловить эту поломку. При смене parse_HealFractionAtOne_Throws проверь это утверждение.");
    }

    @Test
    void parse_TeamMaxBelowMin_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> IntellectTuning.parse(VALID.replace("\"teamMax\": 3", "\"teamMax\": 1"), "intellect.json"),
                "Максимум команды ниже минимума отвергается: экспедицию нельзя собрать ни при каком составе"
                        + " Иначе тест parse_TeamMaxBelowMin_Throws перестанет ловить эту поломку. При смене parse_TeamMaxBelowMin_Throws проверь это утверждение.");
    }

    @Test
    void parse_FoodDemandAboveScale_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> IntellectTuning.parse(
                        VALID.replace("\"foodDemandMax\": 9.0", "\"foodDemandMax\": 20.0"), "intellect.json"),
                "Спрос еды выше шкалы MAX_DEMAND отвергается: распределение сравнивало бы значения вне шкалы"
                        + " Иначе тест parse_FoodDemandAboveScale_Throws перестанет ловить эту поломку. При смене parse_FoodDemandAboveScale_Throws проверь это утверждение.");
    }

    @Test
    void parse_NegativeCount_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> IntellectTuning.parse(
                        VALID.replace("\"forageFishCount\": 1", "\"forageFishCount\": 0"), "intellect.json"),
                "Нулевой счётчик добычи отвергается: задача с count 0 недостижима"
                        + " Иначе тест parse_NegativeCount_Throws перестанет ловить эту поломку. При смене parse_NegativeCount_Throws проверь это утверждение.");
    }
}
