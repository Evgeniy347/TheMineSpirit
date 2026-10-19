package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DecisionSource;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalState;
import com.theminespirit.core.tactics.TacticsMemory;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TacticsStateCodecTests {
    static StoredTactics full() {
        TacticsMemory memory = new TacticsMemory(
                Optional.of(TacticalOption.RETREAT),
                DecisionSource.RULE,
                1200,
                TacticalState.RETREATING,
                1180,
                CombatMode.DEFENSIVE,
                CombatMode.AGGRESSIVE,
                Optional.of(new Point(10.5, 64, -3.25)),
                1190,
                0.35,
                1100);
        return new StoredTactics(
                CombatMode.AGGRESSIVE,
                Optional.of(new OrdersBuilder.Zone(new Point(1, 2, 3), 12)),
                Optional.of("0b9c3c1e-0000-4000-8000-000000000001"),
                Optional.of(memory),
                new RetreatTracker(RetreatMethod.HOME, 1185));
    }

    static StoredTactics minimal() {
        return new StoredTactics(
                CombatMode.DEFENSIVE, Optional.empty(), Optional.empty(), Optional.empty(), RetreatTracker.IDLE);
    }

    @Test
    void decode_EncodedFullState_RestoresEveryField() {
        StoredTactics restored = TacticsStateCodec.decode(TacticsStateCodec.encode(full()));

        assertEquals(
                full(),
                restored,
                "Сохранение духа обязано вернуть память тактики, зону, подопечного и "
                        + "исполняемый отход без потерь, иначе после перезапуска дух забудет бой");
    }

    @Test
    void decode_EncodedMinimalState_RestoresIt() {
        StoredTactics restored = TacticsStateCodec.decode(TacticsStateCodec.encode(minimal()));

        assertEquals(minimal(), restored, "Дух, ни разу не дравшийся, сохраняется без памяти и возвращается таким же");
    }

    @Test
    void encode_Full_CarriesCurrentVersion() {
        String json = TacticsStateCodec.encode(full());

        assertTrue(
                json.contains("\"version\":" + TacticsStateCodec.VERSION),
                "Состояние тактики пишется с версией формата: читатель выбирает разбор по ней");
    }

    record Corrupt(String name, String json) {}

    static Stream<Corrupt> corrupt() {
        String valid = TacticsStateCodec.encode(minimal());
        return Stream.of(
                new Corrupt("не JSON", "{не json"),
                new Corrupt("не объект", "[1,2,3]"),
                new Corrupt("хвост после JSON", valid + "{}"),
                new Corrupt("нет версии", valid.replace("\"version\":1,", "")),
                new Corrupt("версия новее", valid.replace("\"version\":1", "\"version\":99")),
                new Corrupt("версия ноль", valid.replace("\"version\":1", "\"version\":0")),
                new Corrupt("неизвестный режим", valid.replace("DEFENSIVE", "BERSERK")),
                new Corrupt("нет отхода", valid.replace(",\"retreat\":{\"method\":\"NONE\",\"since\":0}", "")),
                new Corrupt("простой с прогрессом", valid.replace("\"since\":0", "\"since\":5")),
                new Corrupt("отход строкой", valid.replace("\"since\":0", "\"since\":\"x\"")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corrupt")
    void decode_CorruptState_ThrowsFormatException(Corrupt corrupt) {
        assertThrows(
                TacticsFormatException.class,
                () -> TacticsStateCodec.decode(corrupt.json()),
                "Повреждённое состояние («" + corrupt.name() + "») обязано кончаться ошибкой, а не тихим "
                        + "значением по умолчанию: молчаливый сброс прячет потерю памяти боя");
    }

    @Test
    void decode_BrokenMemory_ThrowsFormatExceptionWithReason() {
        String json = TacticsStateCodec.encode(full()).replace("\"hpWindowRef\":0.35", "\"hpWindowRef\":7.5");

        TacticsFormatException error = assertThrows(
                TacticsFormatException.class,
                () -> TacticsStateCodec.decode(json),
                "Доля HP больше единицы — не память тактики, а повреждение");

        assertTrue(
                error.getMessage().contains("SpiritTactics"),
                "Сообщение называет ключ, который проверять: " + error.getMessage());
    }
}
