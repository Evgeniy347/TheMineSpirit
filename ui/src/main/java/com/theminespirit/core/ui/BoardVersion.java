package com.theminespirit.core.ui;

import com.theminespirit.core.rest.BoardListings;
import com.theminespirit.core.rest.BoardTask;
import java.util.List;

public final class BoardVersion {
    private BoardVersion() {}

    public static long ofTasks(List<BoardTask> tasks) {
        return BoardListings.versionOf(tasks);
    }
}
