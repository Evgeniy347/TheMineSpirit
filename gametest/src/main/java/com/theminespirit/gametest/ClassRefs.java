package com.theminespirit.gametest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

final class ClassRefs {
    private static final int MAGIC = 0xCAFEBABE;

    private ClassRefs() {}

    static Set<String> of(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Нет байт класса " + type.getName() + " (" + resource
                        + "): фильтр не увидит, какие " + "классы-владельцы шагов зовёт эта сцена");
            }
            return parse(stream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Не прочитан класс " + type.getName() + ": фильтр не увидит ссылки сцены на классы шагов", e);
        }
    }

    static Set<String> parse(byte[] bytes) {
        Set<String> references = new HashSet<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Не файл класса: нет сигнатуры 0xCAFEBABE, ссылки не прочитать");
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] names = new String[count];
            int[] classes = new int[count];
            int index = 1;
            while (index < count) {
                int tag = in.readUnsignedByte();
                int step = 1;
                switch (tag) {
                    case 1 -> names[index] = in.readUTF();
                    case 7 -> classes[index] = in.readUnsignedShort();
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 15 -> in.skipNBytes(3);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                    case 5, 6 -> {
                        in.skipNBytes(8);
                        step = 2;
                    }
                    default ->
                        throw new IllegalArgumentException(
                                "Неизвестная запись constant pool (" + tag + "): формат класса не разобрать");
                }
                index += step;
            }
            for (int slot = 1; slot < count; slot++) {
                if (classes[slot] > 0 && classes[slot] < count && names[classes[slot]] != null) {
                    references.add(names[classes[slot]]);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Класс оборвался на constant pool: ссылки не прочитать", e);
        }
        return references;
    }
}
