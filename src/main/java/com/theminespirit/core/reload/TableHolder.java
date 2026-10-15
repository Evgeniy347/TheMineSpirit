package com.theminespirit.core.reload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Держатель текущей версии одной таблицы. Читатели берут {@link #current()}; загрузчик заменяет версию целиком
 * ({@link #replace}) одной атомарной записью ссылки. Неудачная загрузка ({@link #failed}) оставляет прежнюю версию
 * в силе и лишь помечает статус ошибкой — «тихий fallback» запрещён (docs/CONVENTIONS.md → «Ошибки»), поэтому
 * ошибка видна в статусе и логе.
 */
public final class TableHolder<T> {
    private final String id;
    private final AtomicReference<Entry<T>> current;

    public TableHolder(String id, T initial) {
        this(id, initial, TableStatus.Source.BUILTIN, "classpath", hashOf(String.valueOf(initial)), Clock.systemUTC());
    }

    public TableHolder(String id, T initial, TableStatus.Source source, String origin, String hash, Clock clock) {
        Objects.requireNonNull(id, "id держателя не может быть null: по нему /spirit tables находит таблицу");
        Objects.requireNonNull(initial, "Начальная версия таблицы не может быть null: читатели не должны видеть пустоту");
        this.id = id;
        this.current = new AtomicReference<>(
                new Entry<>(initial, new TableStatus(id, source, origin, Instant.now(clock), hash, null)));
    }

    public String id() {
        return id;
    }

    public T current() {
        return current.get().value();
    }

    public TableStatus status() {
        return current.get().status();
    }

    /** Атомарная замена версии: читатель либо видит старую, либо новую, промежуточного состояния нет. */
    public void replace(T next, TableStatus.Source source, String origin, String hash, Clock clock) {
        Objects.requireNonNull(next, "Новая версия таблицы не может быть null: пустая подмена запрещена, ошибку видно");
        current.set(new Entry<>(next, new TableStatus(id, source, origin, Instant.now(clock), hash, null)));
    }

    /**
     * Неудачная загрузка: прежняя версия остаётся в силе, статус получает текст ошибки. Возвращает статус,
     * чтобы вызывающий показал его в логе и в ответе команды.
     */
    public TableStatus failed(String message) {
        Objects.requireNonNull(message, "Текст ошибки не может быть null: ошибка обязана быть видимой, а не молчаливой");
        return current.updateAndGet(entry -> new Entry<>(entry.value(),
                new TableStatus(id, entry.status().source(), entry.status().origin(), entry.status().loadedAt(),
                        entry.status().hash(), message))).status();
    }

    static String hashOf(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                text.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return text.substring(0, 12);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 недоступен в JVM: без хэша нельзя показать, что таблица поменялась", error);
        }
    }

    private record Entry<T>(T value, TableStatus status) {
    }
}