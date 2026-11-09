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
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> names[index] = in.readUTF();
                    case 7 -> classes[index] = in.readUnsignedShort();
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 15 -> in.skipBytes(3);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        index++;
                    }
                    default ->
                        throw new IllegalArgumentException(
                                "Неизвестная запись constant pool (" + tag + "): формат класса не разобрать");
                }
            }
            for (int index = 1; index < count; index++) {
                if (classes[index] > 0 && classes[index] < count && names[classes[index]] != null) {
                    references.add(names[classes[index]]);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Класс оборвался на constant pool: ссылки не прочитать", e);
        }
        return references;
    }
}
