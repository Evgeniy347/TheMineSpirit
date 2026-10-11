package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.theminespirit.core.rest.ApiError;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.ErrorJson;
import com.theminespirit.core.rest.QueryString;
import com.theminespirit.core.rest.RestRequest;
import com.theminespirit.core.rest.RestResponse;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.Router;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
    private static final int STOP_DELAY_SECONDS = 1;
    private static final int POOL_TERMINATION_SECONDS = 2;

    private final HttpServer httpServer;
    private final ExecutorService pool;
    private final Router router;
    private final AtomicBoolean stopped = new AtomicBoolean();

    private RestServer(HttpServer httpServer, ExecutorService pool, Router router) {
        this.httpServer = httpServer;
        this.pool = pool;
        this.router = router;
    }

    public static RestServer start(InetAddress address, int port, Router router) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(RestTuning.HTTP_THREADS, new RestThreadFactory());
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(address, port), 0);
            RestServer server = new RestServer(httpServer, pool, router);
            httpServer.setExecutor(pool);
            httpServer.createContext("/", server::handle);
            httpServer.start();
            return server;
        } catch (IOException | RuntimeException e) {
            pool.shutdownNow();
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
        httpServer.stop(STOP_DELAY_SECONDS);
        pool.shutdown();
        try {
            if (!pool.awaitTermination(POOL_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("REST-мост: потоки обработчиков не завершились за {} с, прерываю", POOL_TERMINATION_SECONDS);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            write(exchange, answer(exchange));
        } catch (IOException e) {
            LOGGER.warn("REST-мост: ответ клиенту не отправлен (клиент закрыл соединение?): {}", e.toString());
        } finally {
            exchange.close();
        }
    }

    private RestResponse answer(HttpExchange exchange) {
        String description = exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath();
        try {
            return router.handle(read(exchange));
        } catch (ApiException e) {
            return ErrorJson.response(e);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("REST-мост: необработанная ошибка при обработке {}", description, e);
            return ErrorJson.response(ApiError.of(ErrorCode.INTERNAL,
                    "Внутренняя ошибка моста при обработке запроса: подробности в логе сервера"));
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
        }
        return new RestRequest(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                QueryString.parse(exchange.getRequestURI().getRawQuery()), headers, body);
    }

    private static void write(HttpExchange exchange, RestResponse response) throws IOException {
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
