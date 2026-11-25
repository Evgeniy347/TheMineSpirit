package com.theminespirit.core.town;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PackedText {
    private PackedText() {}

    public static byte[] pack(String text) {
        Objects.requireNonNull(text, "Текст не может быть null: сжимать нечего");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Текст не сжат: " + error.getMessage(), error);
        }
    }

    public static String unpack(byte[] packed) {
        Objects.requireNonNull(packed, "Сжатый текст не может быть null: читать нечего");
        if (packed.length == 0) {
            throw new IllegalArgumentException("Сжатый текст пуст: сохранённые данные потеряны");
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Сжатый текст не распакован: " + error.getMessage(), error);
        }
    }
}
