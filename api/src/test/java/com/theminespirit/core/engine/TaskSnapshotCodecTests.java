package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.TaskProgress.ConditionAt;
import com.theminespirit.core.engine.TaskProgress.ConditionBranch;
import com.theminespirit.core.engine.TaskProgress.FallbackAt;
import com.theminespirit.core.engine.TaskProgress.ParallelAt;
import com.theminespirit.core.engine.TaskProgress.ParallelChild;
import com.theminespirit.core.engine.TaskProgress.RepeatAt;
import com.theminespirit.core.engine.TaskProgress.ScriptAt;
import com.theminespirit.core.engine.TaskProgress.SequenceAt;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TaskSnapshotCodecTests {
    private static final Path GOLDEN_DIR = Path.of("src/test/resources/snapshots");

    @Test
    void golden_V1Files_StillDecode() throws IOException {
        assertTrue(
                Files.isDirectory(GOLDEN_DIR),
                "Золотые файлы снимка обязаны лежать в " + GOLDEN_DIR
                        + ": без них формат v1 никто не защищает Иначе тест golden_V1Files_StillDecode перестанет ловить эту поломку. При смене golden_V1Files_StillDecode проверь это утверждение.");
        List<String> failures = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> stream = Files.list(GOLDEN_DIR)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertTrue(
                files.size() >= 9,
                "Золотых файлов меньше девяти (по одному на тип узла и статус): формат потерял образцы. Найдено  Иначе тест golden_V1Files_StillDecode перестанет ловить эту поломку. При смене golden_V1Files_StillDecode проверь это утверждение."
                        + files.size());
        for (Path file : files) {
            String text = Files.readString(file);
            try {
                TaskSnapshotCodec.decode(text);
            } catch (RuntimeException error) {
                failures.add(file.getFileName() + ": " + error.getMessage());
            }
        }

        assertEquals(
                List.of(),
                failures,
                "формат v1 изменён: подними version и добавь читателя старой версии; золотые файлы не правятся Иначе тест golden_V1Files_StillDecode перестанет ловить эту поломку. При смене golden_V1Files_StillDecode проверь это утверждение.");
    }

    @Test
    void encode_ScriptSnapshot_RoundTripsWithState() {
        TaskSnapshot snapshot = running(
                new ScriptCall("goto", Map.of("x", 1, "y", 64, "z", 2)),
                new ScriptAt(Map.of("elapsed", 3, "flags", List.of(true, false), "label", "цель")));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок скрипта с состоянием обязан переживать encode→decode без потерь: потерянное состояние "
                        + "заставит счётный скрипт начать счёт заново Иначе тест encode_ScriptSnapshot_RoundTripsWithState перестанет ловить эту поломку. При смене encode_ScriptSnapshot_RoundTripsWithState проверь это утверждение.");
    }

    @Test
    void encode_SequenceSnapshot_RoundTrips() {
        TaskNode plan = new SequenceNode(
                List.of(new ScriptCall("wait", Map.of("ticks", 5)), new ScriptCall("say", Map.of("text", "go"))));
        TaskSnapshot snapshot = running(plan, new SequenceAt(1, new ScriptAt(Map.of())));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок sequence обязан сохранять индекс и потомка: иначе после перезапуска дух вернётся к шагу 0 При смене encode_SequenceSnapshot_RoundTrips проверь это утверждение.");
    }

    @Test
    void decode_PlanOverPlannerLimit_RestoresRunningPlan() {
        List<TaskNode> steps = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            steps.add(new ScriptCall("wait", Map.of("ticks", 1)));
        }
        TaskSnapshot snapshot = running(new SequenceNode(List.copyOf(steps)), new SequenceAt(0, null));

        TaskSnapshot restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot));

        assertEquals(
                snapshot,
                restored,
                "Снимок внутреннего плана длиннее лимита ИИ-планировщика обязан восстанавливаться: иначе ремонт "
                        + "поселения на 1422 блока теряется после рестарта, хотя задача исправно сохранена При смене decode_PlanOverPlannerLimit_RestoresRunningPlan проверь это утверждение.");
    }

    @Test
    void encode_RepeatSnapshot_RoundTrips() {
        TaskSnapshot snapshot = running(
                new RepeatNode(5, new ScriptCall("wait", Map.of("ticks", 1))), new RepeatAt(2, new ScriptAt(Map.of())));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок repeat обязан сохранять число прошедших повторов: иначе дух выполнит лишние итерации При смене encode_RepeatSnapshot_RoundTrips проверь это утверждение.");
    }

    @Test
    void encode_FallbackSnapshot_RoundTripsWithLastReason() {
        TaskNode plan = new FallbackNode(List.of(new ScriptCall("a", Map.of()), new ScriptCall("b", Map.of())));
        TaskSnapshot snapshot = running(plan, new FallbackAt(1, "первая ветка не смогла", new ScriptAt(Map.of())));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок fallback обязан сохранять индекс ветки и причину провала: потеря причины скрыла бы, "
                        + "почему резервная ветка понадобилась Иначе тест encode_FallbackSnapshot_RoundTripsWithLastReason перестанет ловить эту поломку. При смене encode_FallbackSnapshot_RoundTripsWithLastReason проверь это утверждение.");
    }

    @Test
    void encode_ParallelSnapshot_RoundTripsChildStatuses() {
        TaskNode plan = new ParallelNode(
                ParallelMode.ALL,
                List.of(new ScriptCall("a", Map.of()), new ScriptCall("b", Map.of()), new ScriptCall("c", Map.of())));
        TaskSnapshot snapshot = running(
                plan,
                new ParallelAt(List.of(
                        new ParallelChild(ScriptStatus.SUCCEEDED, null, null),
                        new ParallelChild(ScriptStatus.FAILED, "не дошёл", null),
                        new ParallelChild(ScriptStatus.RUNNING, null, new ScriptAt(Map.of())))));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок parallel обязан сохранять статус и причину каждого потомка: без них завершённые шаги "
                        + "запустятся заново, а провал замалчивается Иначе тест encode_ParallelSnapshot_RoundTripsChildStatuses перестанет ловить эту поломку. При смене encode_ParallelSnapshot_RoundTripsChildStatuses проверь это утверждение.");
    }

    @Test
    void encode_ConditionSnapshot_RoundTripsChosenBranch() {
        TaskNode plan =
                new ConditionNode("seen", Map.of(), new ScriptCall("a", Map.of()), new ScriptCall("b", Map.of()));
        TaskSnapshot snapshot = running(plan, new ConditionAt(ConditionBranch.THEN, new ScriptAt(Map.of())));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок condition обязан сохранять выбранную ветку: иначе после перезапуска условие вычислится "
                        + "второй раз и может уйти в другую сторону При смене encode_ConditionSnapshot_RoundTripsChosenBranch проверь это утверждение.");
    }

    @Test
    void encode_UnresolvedConditionSnapshot_RoundTrips() {
        TaskNode plan =
                new ConditionNode("seen", Map.of(), new ScriptCall("a", Map.of()), new ScriptCall("b", Map.of()));
        TaskSnapshot snapshot = running(plan, new ConditionAt(ConditionBranch.UNRESOLVED, null));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "невычисленное condition обязано восстанавливаться как unresolved: тогда проверка выполнится "
                        + "на первом тике после загрузки, а не потеряется При смене encode_UnresolvedConditionSnapshot_RoundTrips проверь это утверждение.");
    }

    @Test
    void encode_TerminalSnapshots_RoundTripWithoutPlan() {
        TaskSnapshot succeeded = new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.SUCCEEDED, null, null, null);
        TaskSnapshot failed = new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.FAILED, "не дошёл", null, null);
        TaskSnapshot cancelled = new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.CANCELLED, null, null, null);

        assertEquals(
                succeeded,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(succeeded)),
                "снимок SUCCEEDED обязан читаться обратно без плана: завершённая задача хранит только итог Иначе тест encode_TerminalSnapshots_RoundTripWithoutPlan перестанет ловить эту поломку. При смене encode_TerminalSnapshots_RoundTripWithoutPlan проверь это утверждение.");
        assertEquals(
                failed,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(failed)),
                "снимок FAILED обязан читаться обратно вместе с причиной: без причины отчёт о задаче теряет смысл Иначе тест encode_TerminalSnapshots_RoundTripWithoutPlan перестанет ловить эту поломку. При смене encode_TerminalSnapshots_RoundTripWithoutPlan проверь это утверждение.");
        assertEquals(
                cancelled,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(cancelled)),
                "снимок CANCELLED обязан читаться обратно без плана: отменённая задача не должна оживать Иначе тест encode_TerminalSnapshots_RoundTripWithoutPlan перестанет ловить эту поломку. При смене encode_TerminalSnapshots_RoundTripWithoutPlan проверь это утверждение.");
    }

    @Test
    void decode_UnknownVersion999_IsRejectedNamingVersion() {
        String json = "{\"version\":999,\"status\":\"CANCELLED\"}";

        SnapshotFormatException error = assertThrows(
                SnapshotFormatException.class,
                () -> TaskSnapshotCodec.decode(json),
                "снимок будущей версии обязан отвергаться: молчаливый разбор незнакомого формата создал бы "
                        + "задачу из мусора Иначе тест decode_UnknownVersion999_IsRejectedNamingVersion перестанет ловить эту поломку. При смене decode_UnknownVersion999_IsRejectedNamingVersion проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("999"),
                "отказ обязан называть версию 999: по ней понятно, что мод старее снимка. Фактическое:  Иначе тест decode_UnknownVersion999_IsRejectedNamingVersion перестанет ловить эту поломку. При смене decode_UnknownVersion999_IsRejectedNamingVersion проверь это утверждение."
                        + error.getMessage());
    }

    @Test
    void decode_BrokenJson_IsRejectedWithFormatCause() {
        SnapshotFormatException error = assertThrows(
                SnapshotFormatException.class,
                () -> TaskSnapshotCodec.decode("{not json"),
                "битый JSON в ключе SpiritTask не должен ронять загрузку сущности исключением без контекста Иначе тест decode_BrokenJson_IsRejectedWithFormatCause перестанет ловить эту поломку. При смене decode_BrokenJson_IsRejectedWithFormatCause проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("SpiritTask"),
                "отказ обязан называть ключ SpiritTask: по нему видно, что чинить и где. Фактическое:  Иначе тест decode_BrokenJson_IsRejectedWithFormatCause перестанет ловить эту поломку. При смене decode_BrokenJson_IsRejectedWithFormatCause проверь это утверждение."
                        + error.getMessage());
    }

    @Test
    void decode_IndexBeyondPlan_IsRejected() {
        TaskNode plan = new SequenceNode(List.of(new ScriptCall("a", Map.of())));
        TaskSnapshot snapshot = running(plan, new SequenceAt(5, null));

        SnapshotFormatException error = assertThrows(
                SnapshotFormatException.class,
                () -> TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "прогресс, ссылающийся на несуществующий шаг, обязан отвергаться: иначе восстановление "
                        + "полезло бы за границу списка шагов При смене decode_IndexBeyondPlan_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("5"),
                "отказ обязан называть битый индекс 5. Фактическое:  Иначе тест decode_IndexBeyondPlan_IsRejected перестанет ловить эту поломку. При смене decode_IndexBeyondPlan_IsRejected проверь это утверждение."
                        + error.getMessage());
    }

    @Test
    void decode_ForeignChildType_IsRejected() {
        TaskNode plan = new SequenceNode(List.of(new ScriptCall("a", Map.of())));
        TaskSnapshot snapshot = running(plan, new SequenceAt(0, new ParallelAt(List.of())));

        SnapshotFormatException error = assertThrows(
                SnapshotFormatException.class,
                () -> TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "потомок чужого типа обязан отвергаться: иначе восстановление приведёт к ClassCastException "
                        + "внутри тика сущности При смене decode_ForeignChildType_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("script"),
                "отказ обязан называть узел script, чей прогноз не сошёлся. Фактическое:  Иначе тест decode_ForeignChildType_IsRejected перестанет ловить эту поломку. При смене decode_ForeignChildType_IsRejected проверь это утверждение."
                        + error.getMessage());
    }

    @Test
    void encode_NonJsonStateValue_IsRejected() {
        TaskSnapshot snapshot = running(new ScriptCall("a", Map.of()), new ScriptAt(Map.of("broken", new Object())));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TaskSnapshotCodec.encode(snapshot),
                "не-JSON-совместимое состояние обязано отвергаться при encode: иначе в NBT уедет значение, "
                        + "которое потом не прочитать При смене encode_NonJsonStateValue_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("broken"),
                "отказ обязан называть ключ состояния 'broken'. Фактическое:  Иначе тест encode_NonJsonStateValue_IsRejected перестанет ловить эту поломку. При смене encode_NonJsonStateValue_IsRejected проверь это утверждение."
                        + error.getMessage());
    }

    @Test
    void encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason() {
        TaskSnapshot snapshot = running(new ScriptCall("a", Map.of()), new ScriptAt(Map.of("broken", new Object())));

        TaskSnapshotCodec.SaveEncoded encoded = TaskSnapshotCodec.encodeForSave(snapshot);

        assertTrue(
                encoded.failureReason() != null,
                "безопасное сохранение обязано вернуть причину, чтобы адаптер записал её в лог с uuid Иначе тест encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason перестанет ловить эту поломку. При смене encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason проверь это утверждение.");
        TaskSnapshot saved = TaskSnapshotCodec.decode(encoded.json());
        assertEquals(
                RunStatus.FAILED,
                saved.status(),
                "несохранимое состояние превращает запись в FAILED, а не роняет сохранение чанка Иначе тест encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason перестанет ловить эту поломку. При смене encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason проверь это утверждение.");
        assertTrue(
                saved.reason().startsWith("не удалось сохранить задачу"),
                "причина обязана начинаться с 'не удалось сохранить задачу', а получено '" + saved.reason()
                        + "' Иначе тест encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason перестанет ловить эту поломку. При смене encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason проверь это утверждение.");
    }

    private static TaskSnapshot running(TaskNode plan, TaskProgress progress) {
        return new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.RUNNING, null, plan, progress);
    }
}
