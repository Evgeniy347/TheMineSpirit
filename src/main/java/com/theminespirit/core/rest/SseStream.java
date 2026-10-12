package com.theminespirit.core.rest;

/**
 * Источник кадров SSE. Читается одним потоком HTTP-ответа, закрывается этим же потоком.
 */
public interface SseStream extends AutoCloseable {
    /**
     * Следующий кадр для записи. Может блокироваться до события или таймаута кипалайва.
     *
     * @return готовый кадр или null — поток завершён, соединение можно закрывать
     */
    String nextFrame() throws InterruptedException;

    @Override
    void close();
}
