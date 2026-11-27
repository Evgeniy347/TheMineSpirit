package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.audit.AuditMiss;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.board.audit.ChangeRing;
import com.theminespirit.core.board.audit.ChunkCache;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.GroupRef;
import net.minecraft.server.level.ServerLevel;

public final class AuditMisses {
    private AuditMisses() {}

    public static AuditMiss of(ServerLevel level, GroupRef ref, DiffItem item, String actual, String kind) {
        AuditRegistry audit = TownAuditWatch.peek(level);
        ChunkCache cache = audit == null ? null : audit.cache(item.cell());
        ChangeRing ring = audit == null ? null : audit.ring(item.cell());
        ChangeRing.Change last = ring == null ? null : ring.last();
        AuditMiss.Signs signs = new AuditMiss.Signs(
                cache != null && cache.filledTick() >= 0 && cache.known(item.cell()),
                ring != null && ring.seenSince(item.cell(), cache == null ? Long.MIN_VALUE : cache.filledTick()),
                item.satisfiedBy(actual),
                AuditMiss.ALREADY_DONE.equals(kind));
        return AuditMiss.of(
                AuditMiss.classify(signs),
                kind,
                item.cell(),
                item.found(),
                actual,
                item.wanted(),
                new AuditMiss.Context(
                        ref.id(),
                        ref.revision(),
                        cache == null ? -1 : cache.filledTick(),
                        cache == null ? -1 : cache.lastVisit(),
                        ring == null ? 0 : ring.size(),
                        last == null ? "" : last.text(),
                        kind));
    }
}
