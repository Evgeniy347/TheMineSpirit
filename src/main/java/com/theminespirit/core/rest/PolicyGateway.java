package com.theminespirit.core.rest;

import com.theminespirit.core.policy.UnprotectArea;
import java.util.List;

public interface PolicyGateway {

    List<UnprotectArea> areas();

    UnprotectArea grant(UnprotectRequest request);

    UnprotectArea revoke(String id);
}
