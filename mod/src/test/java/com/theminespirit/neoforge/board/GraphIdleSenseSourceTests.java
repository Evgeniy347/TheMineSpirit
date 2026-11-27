package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphIdleSenseSourceTests {
    @Test
    void sense_CompletedChildTicket_IsReopenedForNextIdleEpisode() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/GraphMoreFarmPost.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("if(post.done(id)){post.service.reopen(id);}"),
                "Завершённый локальный scan переоткрывается для нового эпизода простоя: иначе submit "
                        + "видит вечный DONE-дубликат и все шесть духов остаются без работы. При смене "
                        + "sense_CompletedChildTicket_IsReopenedForNextIdleEpisode проверь GraphMoreFarmPost.sense "
                        + "и живой more6_spirit_idle.");
    }
}
