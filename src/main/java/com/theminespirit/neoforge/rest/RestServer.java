package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.theminespirit.core.rest.ApiError;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ClientRateLimit;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.ErrorJson;
import com.theminespirit.core.rest.QueryString;
import com.theminespirit.core.rest.RestRequest;
import com.theminespirit.core.rest.RestResponse;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.Router;
import com.theminespirit.core.rest.SseStream;
import com.theminespirit.core.rest.SseWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

public final class RestServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String EVENT_STREAM_CONTENT_TYPE = "text/event-stream; charset=utf-8";
    private static final int CHUNKED_RESPONSE = 0;
    private static final int STOP_DELAY_SECONDS = 1;
    private static final int POOL_TERMINATION_SECONDS = 2;
    private static final int DRAIN_CHUNK_BYTES = 8192;

    private final HttpServer httpServer;
    private final ExecutorService pool;
    private final Router router;
    private final Runnable onStop;
    private final ClientRateLimit rateLimit = new ClientRateLimit();
    private final AtomicBoolean stopped = new AtomicBoolean();

    private RestServer(HttpServer httpServer, Router router, Runnable onStop) {
        this.httpServer = httpServer;
        this.pool = Executors.newFixedThreadPool(RestTuning.HTTP_THREADS, new RestThreadFactory());
        this.router = router;
        this.onStop = onStop;
    }

    public static RestServer start(InetAddress address, int port, Router router, Runnable onStop) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(address, port), 0);
        RestServer server = new RestServer(httpServer, router, onStop);
        try {
            httpServer.setExecutor(server.pool);
            httpServer.createContext("/", server::handle);
            httpServer.start();
            return server;
        } catch (RuntimeException e) {
            server.pool.shutdownNow();
            throw e;
        }
    }

    public int port() {
        return httpServer.getAddress().getPort();
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        onStop.run();
        httpServer.stop(STOP_DELAY_SECONDS);
        pool.shutdown();
        try {
            if (!pool.awaitTermination(POOL_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn(
                        "REST-мост: потоки обработчиков не завершились за {} с, прерываю", POOL_TERMINATION_SECONDS);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            write(exchange, answer(exchange));
        } catch (IOException e) {
            LOGGER.warn("REST-мост: ответ клиенту не отправлен (клиент закрыл соединение?): {}", e.toString());
        }
    }

    private RestResponse answer(HttpExchange exchange) {
        String description =
                exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath();
        try {
            Optional<RestResponse> limited = rateLimit.reject(clientKey(exchange), System.nanoTime());
            if (limited.isPresent()) {
                drain(exchange);
                return limited.get();
            }
            return router.handle(read(exchange));
        } catch (ApiException e) {
            return ErrorJson.response(e);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("REST-мост: необработанная ошибка при обработке {}", description, e);
            return ErrorJson.response(ApiError.of(
                    ErrorCode.INTERNAL, "Внутренняя ошибка моста при обработке запроса: подробности в логе сервера"));
        }
    }

    private static String clientKey(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private static void drain(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            discardRest(in);
        }
    }

    private static RestRequest read(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
            }
        });
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readNBytes(RestTuning.MAX_BODY_BYTES + 1);
            if (body.length > RestTuning.MAX_BODY_BYTES) {
                discardRest(in);
            }
        }
        return new RestRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                QueryString.parse(exchange.getRequestURI().getRawQuery()),
                headers,
                body);
    }

    private static void discardRest(InputStream in) throws IOException {
        byte[] buffer = new byte[DRAIN_CHUNK_BYTES];
        long drained = 0;
        int read;
        while (drained < RestTuning.MAX_DRAIN_BYTES && (read = in.read(buffer)) >= 0) {
            drained += read;
        }
    }

    private static void write(HttpExchange exchange, RestResponse response) throws IOException {
        if (response.stream() != null) {
            writeStream(exchange, response);
            return;
        }
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", JSON_CONTENT_TYPE);
        headers.set("Cache-Control", "no-store");
        response.headers().forEach(headers::set);
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void writeStream(HttpExchange exchange, RestResponse response) {
        try (SseStream stream = response.stream();
                OutputStream out = exchange.getResponseBody()) {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", EVENT_STREAM_CONTENT_TYPE);
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(response.status(), CHUNKED_RESPONSE);
            LOGGER.debug("REST-мост: поток событий закрыт: {}", SseWriter.pump(stream, out));
        } catch (IOException e) {
            LOGGER.debug("REST-мост: поток событий не открыт или закрыт клиентом: {}", e.toString());
        }
    }

    private static final class RestThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "theminespirit-rest-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
