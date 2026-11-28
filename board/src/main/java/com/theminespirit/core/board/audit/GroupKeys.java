package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

final class GroupKeys {
    private GroupKeys() {}

    static Group.Kind kind(SourceRef source) {
        Objects.requireNonNull(source, "Источник клетки не может быть null: группу без вида не собрать");
        return switch (source.kind()) {
            case BUILDING -> Group.Kind.BUILDING;
            case STREET -> Group.Kind.STREET;
            case WALL -> Group.Kind.WALL;
            case MINE -> Group.Kind.MINE;
            case GROUND, NONE -> Group.Kind.GROUND;
        };
    }

    static ChunkCell chunk(SourceRef source, BlockCoord cell) {
        kind(source);
        return ConstructionBatches.at(cell.x(), cell.z());
    }

    static String key(Expectation expectation) {
        Objects.requireNonNull(expectation, "Ожидание не может быть null: ключ группы строится по нему");
        SourceRef source = expectation.source();
        Group.Kind kind = kind(source);
        ChunkCell chunk = chunk(source, expectation.cell());
        return Group.pending(kind, source.subject(), chunk, List.of(), 0).key();
    }
}
