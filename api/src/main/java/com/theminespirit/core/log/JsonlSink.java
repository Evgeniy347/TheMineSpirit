package com.theminespirit.core.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class JsonlSink implements EventLog.Sink {
    private static final String PREFIX = "events-";
    private static final String SUFFIX = ".jsonl";

    private final Path directory;
    private final long rotateBytes;
    private @Nullable BufferedWriter writer;
    private long size;
    private int sequence;

    JsonlSink(Path directory, long rotateBytes) {
        if (rotateBytes < 1) {
            throw new IllegalArgumentException("Предел файла " + rotateBytes + " байт: нужен хотя бы один байт");
        }
        this.directory = directory;
        this.rotateBytes = rotateBytes;
        this.sequence = nextSequence();
    }

    @Override
    public void write(LogRecord record) throws IOException {
        String line = record.toJson() + "\n";
        int length = line.getBytes(StandardCharsets.UTF_8).length;
        if (writer == null || (size > 0 && size + length > rotateBytes)) {
            closeFile();
            openFile();
        }
        Objects.requireNonNull(writer, "Файл событий не открылся: писатель остался null")
                .write(line);
        size += length;
    }

    @Override
    public void close() throws IOException {
        closeFile();
    }

    private void openFile() throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve(fileName(sequence++));
        writer = Files.newBufferedWriter(
                path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        size = 0;
    }

    private void closeFile() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }

    private int nextSequence() {
        int highest = 0;
        if (!Files.isDirectory(directory)) {
            return 1;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, PREFIX + "*" + SUFFIX)) {
            for (Path file : files) {
                highest = Math.max(highest, sequenceOf(file.getFileName().toString()));
            }
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Каталог событий '" + directory + "' не читается: " + error.getMessage(), error);
        }
        return highest + 1;
    }

    private static int sequenceOf(String name) {
        String middle = name.substring(PREFIX.length(), name.length() - SUFFIX.length());
        try {
            return Integer.parseInt(middle);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String fileName(int sequence) {
        return String.format(Locale.ROOT, "%s%05d%s", PREFIX, sequence, SUFFIX);
    }
}
