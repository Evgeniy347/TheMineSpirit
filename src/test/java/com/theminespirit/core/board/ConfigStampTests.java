package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ConfigStampTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void current_FromBoardTable_NotBlank() {
        assertFalse(ConfigStamp.current().isBlank(),
                "Хэш версии конфигурации обязан существовать: журнал без него не отличить по времени, "
                        + "а отсутствие таблицы — не повод вернуть пустую строку");
    }

    @Test
    void current_BoardStatusHash_Matches() {
        assertEquals(DataTables.INSTANCE.board().status().hash(), ConfigStamp.current(),
                "Хэш берётся из статуса таблицы board: по нему видно, что /reload сменил конфигурацию");
    }

    @Test
    void of_AfterReplace_ReturnsNewHash() {
        TableHolder<String> holder = new TableHolder<>("board", "v1", TableStatus.Source.MOD, "mod",
                "hash-old", CLOCK);

        holder.replace("v2", TableStatus.Source.DATAPACK, "world/datapacks/p/data/board.json", "hash-new", CLOCK);

        assertEquals("hash-new", ConfigStamp.of(holder),
                "После замены таблицы хэш новый: записи после /reload несут новую версию конфигурации");
    }

    @Test
    void of_NullHolder_Rejected() {
        assertThrows(NullPointerException.class, () -> ConfigStamp.of(null),
                "Держатель null обязан отвергаться: без него неизвестен хэш версии конфигурации");
    }
}
