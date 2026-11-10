package com.theminespirit.core.board;

public final class PersistBatch {
    private int depth;

    public void begin() {
        depth++;
    }

    public boolean end() {
        if (depth == 0) {
            throw new IllegalStateException(
                    "Конец пакета сохранения без начала: пакет открывают begin() перед изменениями, иначе "
                            + "запись на диск потеряется или случится не в свою очередь");
        }
        depth--;
        return depth == 0;
    }

    public boolean deferred() {
        return depth > 0;
    }
}
