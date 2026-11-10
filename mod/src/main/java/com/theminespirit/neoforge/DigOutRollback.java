package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.board.lease.LeaseKind;
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
        boolean spoilPending = false;
        for (LeaseCell cell : lease.cells()) {
            if (cell.kind() == LeaseKind.BROKEN) {
                spoilPending = true;
                break;
            }
        }
        if (spoilPending) {
            lease.orphan();
            return;
        }
        lease.commit();
    }
}
