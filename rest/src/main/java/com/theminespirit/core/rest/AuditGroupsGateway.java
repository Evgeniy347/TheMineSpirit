package com.theminespirit.core.rest;

import com.theminespirit.core.board.audit.Group;
import java.util.List;

public interface AuditGroupsGateway {
    List<Group> groups();
}
