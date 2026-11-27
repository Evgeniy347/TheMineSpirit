package com.theminespirit.core.rest;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.Group;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuditGroupsJson {
    private AuditGroupsJson() {}

    public static String viewJson(List<Group> groups) {
        List<Object> body = new ArrayList<>();
        for (Group group : groups) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", group.key());
            entry.put("kind", group.kind().name());
            entry.put("subject", group.subject());
            ChunkCell chunk = group.chunk();
            if (chunk == null) {
                entry.put("chunk", null);
            } else {
                Map<String, Integer> chunkBody = new LinkedHashMap<>();
                chunkBody.put("x", chunk.x());
                chunkBody.put("z", chunk.z());
                entry.put("chunk", chunkBody);
            }
            entry.put("pending", group.pending());
            entry.put("total", group.total());
            entry.put("status", group.status().name());
            entry.put("unknown", group.unknown());
            entry.put("revision", group.revision());
            body.add(entry);
        }
        return RestJson.write(Map.of("groups", body));
    }
}
