package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProtectionStampGuardTests {
    private static final Path BLOCKED = SourceFiles.path("neoforge/ClearBlocked.java");

    @Test
    void clearBlocked_ProtectionStamp_IgnoresLeaseRevisions() {
        List<String> offenders = scanned(List.of(Map.entry(BLOCKED, textOf(BLOCKED))));

        assertEquals(
                List.of(),
                offenders,
                "Мемо защиты расчистки снова опирается на ревизии аренды или доски: они меняются при каждой "
                        + "чужой аренде, мемо сбрасывается и полный проход по сотням тысяч клеток повторяется "
                        + "каждый тик — иначе сервер замирает на секунды (JFR 25.11). Ключ мемо — окно времени и "
                        + "поколение плана. При смене ProtectionStampGuardTests проверь ClearSweepRun.pick и "
                        + "ClearBlocked.dropProtected");
    }

    @Test
    void revisionStampPattern_LeaseLookup_IsReported() {
        String sample = "        lease = LeaseData.get(server).revision();\n";

        assertEquals(
                List.of("sample"),
                scanned(List.of(Map.entry(Path.of("sample"), sample))),
                "Сторож ослеп: ключ мемо с ревизией аренды обязан попасть в нарушители, иначе чужой сбор снова "
                        + "будет сбрасывать кэш защиты каждый тик. При смене проверки шаблона проверь пару "
                        + "образцов: ревизия краснеет, окно времени и поколение плана — зелёные");
    }

    @Test
    void windowStampPattern_Generation_IsAccepted() {
        String sample = "        return window * 104729L + PlotCellStructure.generation() * 7919L;\n";

        assertEquals(
                List.of(),
                scanned(List.of(Map.entry(Path.of("sample"), sample))),
                "Сторож ложно срабатывает: ключ из окна времени и поколения плана — законный, без этого кэш "
                        + "защиты не отличит смену плана. При смене проверки шаблона проверь, что образец с "
                        + "generation() остаётся зелёным");
    }

    private static List<String> scanned(List<Map.Entry<Path, String>> sources) {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources) {
            String text = source.getValue();
            if (text.contains("LeaseData") || text.contains(".revision()")) {
                offenders.add(source.getKey().toString());
            }
        }
        return offenders;
    }

    private static String textOf(Path file) {
        for (Map.Entry<Path, String> entry :
                SourceFiles.javaUnder(file.getParent()).entrySet()) {
            if (entry.getKey().getFileName().equals(file.getFileName())) {
                return entry.getValue();
            }
        }
        throw new IllegalStateException("Сторож не нашёл " + file + ": без исходника ключа мемо защиты правило "
                + "«аренда не сбрасывает кэш» не проверить — проверь имя файла и каталог guards");
    }
}
