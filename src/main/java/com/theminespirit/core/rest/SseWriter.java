package com.theminespirit.core.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class SseWriter {
    public enum Outcome {
        FINISHED,
        CLIENT_GONE,
        INTERRUPTED
    }

    private SseWriter() {}

    public static Outcome pump(SseStream stream, OutputStream out) {
        try (stream) {
            String frame;
            while ((frame = stream.nextFrame()) != null) {
                out.write(frame.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return Outcome.FINISHED;
        } catch (IOException clientDisconnected) {
            return Outcome.CLIENT_GONE;
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
            return Outcome.INTERRUPTED;
        }
    }
}
