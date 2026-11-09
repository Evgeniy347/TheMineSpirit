package com.theminespirit.neoforge.board;

final class BoardPersistence {
    private BoardPersistence() {}

    static void close(BoardService service) {
        service.journal().flushAll();
        persist(service);
    }

    static void persist(BoardService service) {
        if (service.store != null) {
            BoardTargetsNbt.persist(service.store, service.board().snapshot(), service.targets, service.sources);
        }
    }

    static void persistBoard(BoardService service) {
        persist(service);
    }
}
