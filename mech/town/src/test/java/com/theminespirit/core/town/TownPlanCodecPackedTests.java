package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TownPlanCodecPackedTests {
    @Test
    void packed_LivePlan_RoundTrips() throws Exception {
        TownPlan live = TownPlanCodec.decode(fixture("/live/warehouselot/town-plan.txt"));

        TownPlan restored = TownPlanCodec.decodePacked(TownPlanCodec.encodePacked(live));

        assertEquals(
                live,
                restored,
                "Сжатый план читается обратно без потерь: строка плана в NBT не переживает перезапуск "
                        + "(план пишется пустым), поэтому сохранение — байты, а не строка; иначе каждый старт мира "
                        + "теряет участки стратега и перепланирует поселение с нуля"
                        + " При смене packed_LivePlan_RoundTrips проверь это утверждение.");
        assertTrue(
                TownPlanCodec.encode(live).length() > 16_000,
                "Живой план длиннее 16 000 символов: именно поэтому тексту плана тесно в строке NBT "
                        + "и его хранят сжатыми байтами"
                        + " Иначе тест packed_LivePlan_RoundTrips перестанет ловить эту поломку. При смене packed_LivePlan_RoundTrips проверь это утверждение.");
    }

    @Test
    void packed_EmptyBytes_Rejected() {
        IllegalArgumentException broken = assertThrows(
                IllegalArgumentException.class,
                () -> TownPlanCodec.decodePacked(new byte[0]),
                "Пустой сжатый план обязан отклоняться понятной ошибкой, а не пустым планом без участков"
                        + " Иначе тест packed_EmptyBytes_Rejected перестанет ловить эту поломку. При смене packed_EmptyBytes_Rejected проверь это утверждение.");
        assertTrue(
                broken.getMessage().contains("сжатый план пуст"),
                "Ошибка обязана называть причину — пустой сжатый план: без этого непонятно, "
                        + "почему мир перепланировал поселение заново; получено: " + broken.getMessage()
                        + " Иначе тест packed_EmptyBytes_Rejected перестанет ловить эту поломку. При смене packed_EmptyBytes_Rejected проверь это утверждение.");
    }

    private static String fixture(String resource) throws Exception {
        try (InputStream in = TownPlanCodecPackedTests.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Нет фикстуры " + resource + ": проверка плана невозможна");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
