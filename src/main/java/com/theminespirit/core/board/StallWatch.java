package com.theminespirit.core.board;

public final class StallWatch {
    public static final long REPORT_TICKS = TaskStall.NO_PROGRESS_TICKS - 200;

    private long signature = Long.MIN_VALUE;
    private long since = -1;
    private boolean reported;

    public boolean stalled(long now, long signature) {
        if (signature != this.signature) {
            this.signature = signature;
            since = now;
            reported = false;
        }
        if (reported || since < 0 || now - since < REPORT_TICKS) {
            return false;
        }
        reported = true;
        return true;
    }
}
