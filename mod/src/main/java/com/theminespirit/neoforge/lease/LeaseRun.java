package com.theminespirit.neoforge.lease;

import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.board.lease.LeaseSession;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class LeaseRun {
    private final LeaseData data;
    private final LeaseSession session;

    private LeaseRun(LeaseData data, LeaseSession session) {
        this.data = data;
        this.session = session;
    }

    public static LeaseRun open(SpiritEntity spirit, String purpose) {
        Objects.requireNonNull(spirit, "Дух не может быть null: аренда принадлежит ему");
        if (!(spirit.level() instanceof ServerLevel level) || level.isClientSide()) {
            throw new IllegalStateException("Аренду временной правки открывают только на сервере: на клиенте мира нет");
        }
        LeaseData data = LeaseData.get(level);
        LeaseSession session = LeaseSession.open(data.book(), spirit.getUUID(), null, purpose, level.getGameTime());
        data.changed();
        return new LeaseRun(data, session);
    }

    public static @Nullable LeaseRun ownedAt(SpiritEntity spirit, BlockPos pos) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return null;
        }
        LeaseData data = LeaseData.get(level);
        return data.book()
                .activeOwner(coord(pos))
                .filter(lease -> lease.ownedBy(spirit.getUUID()))
                .map(lease -> new LeaseRun(data, LeaseSession.attach(data.book(), lease.id())))
                .orElse(null);
    }

    public static @Nullable LeaseRun attach(ServerLevel level, String id) {
        LeaseData data = LeaseData.get(level);
        return data.book()
                .find(id)
                .map(lease -> new LeaseRun(data, LeaseSession.attach(data.book(), lease.id())))
                .orElse(null);
    }

    public String id() {
        return session.id();
    }

    public String purpose() {
        return session.purpose();
    }

    public LeaseStatus status() {
        return session.status();
    }

    public List<LeaseCell> cells() {
        return session.cells();
    }

    public boolean contains(BlockPos pos) {
        return session.contains(coord(pos));
    }

    public void placed(BlockPos pos) {
        if (session.placed(coord(pos))) {
            data.changed();
        }
    }

    public void placed(BlockPos pos, String block) {
        if (session.placed(coord(pos), block)) {
            data.changed();
        }
    }

    public void broken(BlockPos pos, BlockState before) {
        if (session.broken(coord(pos), BlockStates.idOf(before))) {
            data.changed();
        }
    }

    public void broken(BlockPos pos, String before) {
        if (session.broken(coord(pos), before)) {
            data.changed();
        }
    }

    public void state(BlockPos pos, String before, String after) {
        if (session.state(coord(pos), before, after)) {
            data.changed();
        }
    }

    public void rolledBack(BlockPos pos) {
        data.book().rollback(session.id(), coord(pos));
        data.changed();
    }

    public void forget(BlockPos pos) {
        session.forget(coord(pos));
        data.changed();
    }

    public void commit() {
        if (session.status() != LeaseStatus.ACTIVE) {
            return;
        }
        session.commit();
        data.changed();
    }

    public void orphan() {
        if (session.status() != LeaseStatus.ACTIVE) {
            return;
        }
        session.orphan();
        data.changed();
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
