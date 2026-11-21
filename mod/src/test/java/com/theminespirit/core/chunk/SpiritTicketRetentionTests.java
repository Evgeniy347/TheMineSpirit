package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpiritTicketRetentionTests {
    @Test
    void keepAtLoad_WithDeadOwner_RemovesOnlyDeadTickets() {
        UUID live = UUID.randomUUID();
        UUID dead = UUID.randomUUID();

        Set<UUID> keep = SpiritTicketRetention.keepAtLoad(Set.of(live, dead), Optional.of(Set.of(live)));

        assertEquals(
                Set.of(live),
                keep,
                "Билеты владельца, отсутствующего в реестре живых духов, обязаны сниматься: иначе мёртвый дух "
                        + "держит чанки нагруженными до перезапуска"
                        + " При смене keepAtLoad_WithDeadOwner_RemovesOnlyDeadTickets проверь это утверждение.");
    }

    @Test
    void keepAtLoad_WithAllOwnersLive_KeepsEveryTicket() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        Set<UUID> keep = SpiritTicketRetention.keepAtLoad(Set.of(first, second), Optional.of(Set.of(first, second)));

        assertEquals(
                Set.of(first, second),
                keep,
                "Если все владельцы живы, снимать нечего: потеря билета живого духа оставит его без тикающих чанков"
                        + " Иначе тест keepAtLoad_WithAllOwnersLive_KeepsEveryTicket перестанет ловить эту поломку. При смене keepAtLoad_WithAllOwnersLive_KeepsEveryTicket проверь это утверждение.");
    }

    @Test
    void keepAtLoad_WithEmptyKnownLiveSpirits_KeepsNothing() {
        UUID owner = UUID.randomUUID();

        Set<UUID> keep = SpiritTicketRetention.keepAtLoad(Set.of(owner), Optional.of(Set.of()));

        assertEquals(
                Set.of(),
                keep,
                "Пустой реестр живых духов означает, что владельцев в живых нет: билеты обязаны сняться. "
                        + "Иначе удалённые духи держат чанки вечно"
                        + " При смене keepAtLoad_WithEmptyKnownLiveSpirits_KeepsNothing проверь это утверждение.");
    }

    @Test
    void keepAtLoad_RegistryMissing_KeepsEveryTicket() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        Set<UUID> keep = SpiritTicketRetention.keepAtLoad(Set.of(first, second), Optional.empty());

        assertEquals(
                Set.of(first, second),
                keep,
                "Без реестра (мир до его появления) сущности ещё не загружены и мёртвых владельцев не опознать: "
                        + "сохранить надо все билеты, иначе первая же загрузка мира снимет чанки всех живых духов"
                        + " При смене keepAtLoad_RegistryMissing_KeepsEveryTicket проверь это утверждение.");
    }
}
