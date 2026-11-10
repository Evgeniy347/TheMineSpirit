package com.theminespirit.neoforge;

import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.List;
import net.minecraft.core.BlockPos;

final class DigOutRollback {
    private final LeaseRun lease;

    DigOutRollback(LeaseRun lease) {
        this.lease = lease;
    }

    void forgetReturned(List<BlockPos> returned) {
        for (BlockPos pos : returned) {
            if (lease.contains(pos)) {
                lease.forget(pos);
            }
        }
    }

    void close() {
        if (lease.cells().isEmpty()) {
            lease.commit();
            return;
        }
        lease.orphan();
    }
}
