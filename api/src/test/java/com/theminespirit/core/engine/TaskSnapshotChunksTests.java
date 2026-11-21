package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskSnapshotChunksTests {
    private static final int SNAPSHOT_CHARS = 250_000;

    @Test
    void splitSnapshot_Large250Kb_ChunksFitNbtStringAndJoinBack() throws IOException {
        String json = largeSnapshot(SNAPSHOT_CHARS);
        assertThrows(
                UTFDataFormatException.class,
                () -> writeNbtString(json),
                "Снимок на " + json.length() + " символов обязан не влезать в одну строку NBT: иначе тест "
                        + "не воспроизводит потерю задачи духа при автосейве и ничего не доказывает"
                        + " При смене splitSnapshot_Large250Kb_ChunksFitNbtStringAndJoinBack проверь это утверждение.");

        List<String> chunks = TaskSnapshotChunks.split(json);
        assertTrue(
                chunks.size() > 1,
                "Снимок на " + json.length() + " символов разбит на " + chunks.size()
                        + " кусков: без разбиения NBT-строка снова упадёт с UTFDataFormatException"
                        + " Иначе тест splitSnapshot_Large250Kb_ChunksFitNbtStringAndJoinBack перестанет ловить эту поломку. При смене splitSnapshot_Large250Kb_ChunksFitNbtStringAndJoinBack проверь это утверждение.");

        List<String> readBack = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            assertTrue(
                    !chunk.isEmpty(),
                    "Пустой кусок снимка нечего записывать в NBT: список кусков обязан содержать только непустые строки"
                            + " Иначе тест splitSnapshot_Large250Kb_ChunksFitNbtStringAndJoinBack перестанет ловить эту поломку. При смене splitSnapshot_Large250Kb_ChunksFitNbtStringAndJoinBack проверь это утверждение.");
            readBack.add(readNbtString(writeNbtString(chunk)));
        }
        assertEquals(
                json,
                TaskSnapshotChunks.join(readBack),
                "Склейка кусков после записи и чтения каждой NBT-строки обязана вернуть исходный JSON: иначе после "
                        + "рестарта дух получит повреждённую задачу"
                        + " При смене splitSnapshot_Large250Kb_ChunksFitNbtStringAndJoinBack проверь это утверждение.");
    }

    @Test
    void split_ExactlyOneChunk_KeepsSnapshotWhole() {
        String json = largeSnapshot(TaskSnapshotChunks.MAX_CHUNK_CHARS);
        assertEquals(
                TaskSnapshotChunks.MAX_CHUNK_CHARS,
                json.length(),
                "Подготовка теста сломана: снимок обязан быть ровно в один кусок, иначе граница не проверяется"
                        + " При смене split_ExactlyOneChunk_KeepsSnapshotWhole проверь это утверждение.");
        assertEquals(
                List.of(json),
                TaskSnapshotChunks.split(json),
                "Снимок длиной ровно в предел куска обязан остаться одним куском: лишнее дробление тратит NBT-место"
                        + " Иначе тест split_ExactlyOneChunk_KeepsSnapshotWhole перестанет ловить эту поломку. При смене split_ExactlyOneChunk_KeepsSnapshotWhole проверь это утверждение.");
    }

    @Test
    void split_OneCharOverChunk_ReturnsSecondChunk() {
        String json = largeSnapshot(TaskSnapshotChunks.MAX_CHUNK_CHARS + 1);
        List<String> chunks = TaskSnapshotChunks.split(json);
        assertEquals(
                2,
                chunks.size(),
                "Снимок на один символ длиннее предела обязан дать два куска: иначе последний символ теряется"
                        + " При смене split_OneCharOverChunk_ReturnsSecondChunk проверь это утверждение.");
        assertEquals(
                json,
                TaskSnapshotChunks.join(chunks),
                "Склейка кусков обязана вернуть исходный снимок без потери граничного символа"
                        + " Иначе тест split_OneCharOverChunk_ReturnsSecondChunk перестанет ловить эту поломку. При смене split_OneCharOverChunk_ReturnsSecondChunk проверь это утверждение.");
    }

    @Test
    void split_EmptySnapshot_ReturnsNoChunksAndJoinsEmpty() {
        assertEquals(
                List.of(),
                TaskSnapshotChunks.split(""),
                "Пустой снимок обязан дать пустой список кусков: пустая строка NBT не должна появляться в сохранении"
                        + " Иначе тест split_EmptySnapshot_ReturnsNoChunksAndJoinsEmpty перестанет ловить эту поломку. При смене split_EmptySnapshot_ReturnsNoChunksAndJoinsEmpty проверь это утверждение.");
        assertEquals(
                "",
                TaskSnapshotChunks.join(List.of()),
                "Склейка пустого списка кусков обязана вернуть пустой снимок, а не null"
                        + " Иначе тест split_EmptySnapshot_ReturnsNoChunksAndJoinsEmpty перестанет ловить эту поломку. При смене split_EmptySnapshot_ReturnsNoChunksAndJoinsEmpty проверь это утверждение.");
    }

    @Test
    void split_NullSnapshot_ThrowsWithReason() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> TaskSnapshotChunks.split(null),
                "Null-снимок нельзя молча превратить в пустой список: это стёрло бы задачу духа без следа в логе"
                        + " Иначе тест split_NullSnapshot_ThrowsWithReason перестанет ловить эту поломку. При смене split_NullSnapshot_ThrowsWithReason проверь это утверждение.");
        assertEquals(
                "Снимок задачи не может быть null: делить на куски нечего",
                error.getMessage(),
                "Исключение обязано называть, что делить нечего: иначе в логе автосейва не понять причину"
                        + " При смене split_NullSnapshot_ThrowsWithReason проверь это утверждение.");
    }

    private static String largeSnapshot(int minChars) {
        StringBuilder builder = new StringBuilder(minChars);
        builder.append("{\"версия\":\"снимок задачи духа\",\"план\":[");
        while (builder.length() < minChars) {
            builder.append("{\"скрипт\":\"repair:level\",\"состояние\":\"блок 1422\"},");
        }
        builder.setLength(minChars);
        return builder.toString();
    }

    private static byte[] writeNbtString(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(value);
        }
        return bytes.toByteArray();
    }

    private static String readNbtString(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String value = in.readUTF();
            assertEquals(
                    0,
                    in.available(),
                    "После чтения NBT-строки в буфере не осталось байт: иначе запись и чтение расходятся"
                            + " При смене split_NullSnapshot_ThrowsWithReason проверь это утверждение.");
            return value;
        }
    }
}
