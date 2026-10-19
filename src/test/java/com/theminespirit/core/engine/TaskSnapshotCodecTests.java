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
                "Золотые файлы снимка обязаны лежать в " + GOLDEN_DIR + ": без них формат v1 никто не защищает");
        List<String> failures = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> stream = Files.list(GOLDEN_DIR)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertTrue(
                files.size() >= 9,
                "Золотых файлов меньше девяти (по одному на тип узла и статус): формат потерял образцы. Найдено "
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
                "формат v1 изменён: подними version и добавь читателя старой версии; золотые файлы не правятся");
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
                        + "заставит счётный скрипт начать счёт заново");
    }

    @Test
    void encode_SequenceSnapshot_RoundTrips() {
        TaskNode plan = new SequenceNode(
                List.of(new ScriptCall("wait", Map.of("ticks", 5)), new ScriptCall("say", Map.of("text", "go"))));
        TaskSnapshot snapshot = running(plan, new SequenceAt(1, new ScriptAt(Map.of())));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок sequence обязан сохранять индекс и потомка: иначе после перезапуска дух вернётся к шагу 0");
    }

    @Test
    void encode_RepeatSnapshot_RoundTrips() {
        TaskSnapshot snapshot = running(
                new RepeatNode(5, new ScriptCall("wait", Map.of("ticks", 1))), new RepeatAt(2, new ScriptAt(Map.of())));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок repeat обязан сохранять число прошедших повторов: иначе дух выполнит лишние итерации");
    }

    @Test
    void encode_FallbackSnapshot_RoundTripsWithLastReason() {
        TaskNode plan = new FallbackNode(List.of(new ScriptCall("a", Map.of()), new ScriptCall("b", Map.of())));
        TaskSnapshot snapshot = running(plan, new FallbackAt(1, "первая ветка не смогла", new ScriptAt(Map.of())));

        assertEquals(
                snapshot,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "снимок fallback обязан сохранять индекс ветки и причину провала: потеря причины скрыла бы, "
                        + "почему резервная ветка понадобилась");
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
                        + "запустятся заново, а провал замалчивается");
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
                        + "второй раз и может уйти в другую сторону");
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
                        + "на первом тике после загрузки, а не потеряется");
    }

    @Test
    void encode_TerminalSnapshots_RoundTripWithoutPlan() {
        TaskSnapshot succeeded = new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.SUCCEEDED, null, null, null);
        TaskSnapshot failed = new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.FAILED, "не дошёл", null, null);
        TaskSnapshot cancelled = new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.CANCELLED, null, null, null);

        assertEquals(
                succeeded,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(succeeded)),
                "снимок SUCCEEDED обязан читаться обратно без плана: завершённая задача хранит только итог");
        assertEquals(
                failed,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(failed)),
                "снимок FAILED обязан читаться обратно вместе с причиной: без причины отчёт о задаче теряет смысл");
        assertEquals(
                cancelled,
                TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(cancelled)),
                "снимок CANCELLED обязан читаться обратно без плана: отменённая задача не должна оживать");
    }

    @Test
    void decode_UnknownVersion999_IsRejectedNamingVersion() {
        String json = "{\"version\":999,\"status\":\"CANCELLED\"}";

        SnapshotFormatException error = assertThrows(
                SnapshotFormatException.class,
                () -> TaskSnapshotCodec.decode(json),
                "снимок будущей версии обязан отвергаться: молчаливый разбор незнакомого формата создал бы "
                        + "задачу из мусора");

        assertTrue(
                error.getMessage().contains("999"),
                "отказ обязан называть версию 999: по ней понятно, что мод старее снимка. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void decode_BrokenJson_IsRejectedWithFormatCause() {
        SnapshotFormatException error = assertThrows(
                SnapshotFormatException.class,
                () -> TaskSnapshotCodec.decode("{not json"),
                "битый JSON в ключе SpiritTask не должен ронять загрузку сущности исключением без контекста");

        assertTrue(
                error.getMessage().contains("SpiritTask"),
                "отказ обязан называть ключ SpiritTask: по нему видно, что чинить и где. Фактическое: "
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
                        + "полезло бы за границу списка шагов");

        assertTrue(
                error.getMessage().contains("5"),
                "отказ обязан называть битый индекс 5. Фактическое: " + error.getMessage());
    }

    @Test
    void decode_ForeignChildType_IsRejected() {
        TaskNode plan = new SequenceNode(List.of(new ScriptCall("a", Map.of())));
        TaskSnapshot snapshot = running(plan, new SequenceAt(0, new ParallelAt(List.of())));

        SnapshotFormatException error = assertThrows(
                SnapshotFormatException.class,
                () -> TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot)),
                "потомок чужого типа обязан отвергаться: иначе восстановление приведёт к ClassCastException "
                        + "внутри тика сущности");

        assertTrue(
                error.getMessage().contains("script"),
                "отказ обязан называть узел script, чей прогноз не сошёлся. Фактическое: " + error.getMessage());
    }

    @Test
    void encode_NonJsonStateValue_IsRejected() {
        TaskSnapshot snapshot = running(new ScriptCall("a", Map.of()), new ScriptAt(Map.of("broken", new Object())));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TaskSnapshotCodec.encode(snapshot),
                "не-JSON-совместимое состояние обязано отвергаться при encode: иначе в NBT уедет значение, "
                        + "которое потом не прочитать");

        assertTrue(
                error.getMessage().contains("broken"),
                "отказ обязан называть ключ состояния 'broken'. Фактическое: " + error.getMessage());
    }

    @Test
    void encodeForSave_NonJsonState_ReturnsFailedSnapshotWithReason() {
        TaskSnapshot snapshot = running(new ScriptCall("a", Map.of()), new ScriptAt(Map.of("broken", new Object())));

        TaskSnapshotCodec.SaveEncoded encoded = TaskSnapshotCodec.encodeForSave(snapshot);

        assertTrue(
                encoded.failureReason() != null,
                "безопасное сохранение обязано вернуть причину, чтобы адаптер записал её в лог с uuid");
        TaskSnapshot saved = TaskSnapshotCodec.decode(encoded.json());
        assertEquals(
                RunStatus.FAILED,
                saved.status(),
                "несохранимое состояние превращает запись в FAILED, а не роняет сохранение чанка");
        assertTrue(
                saved.reason().startsWith("не удалось сохранить задачу"),
                "причина обязана начинаться с 'не удалось сохранить задачу', а получено '" + saved.reason() + "'");
    }

    private static TaskSnapshot running(TaskNode plan, TaskProgress progress) {
        return new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.RUNNING, null, plan, progress);
    }
}
