package com.theminespirit.neoforge;

import java.util.stream.Collectors;

public final class CallSource {
    private CallSource() {}

    public static String of(String excludedSimpleName, int limit) {
        return StackWalker.getInstance()
                .walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
                        .filter(name -> name.startsWith("com.theminespirit")
                                && !name.equals(CallSource.class.getName())
                                && !name.endsWith("." + excludedSimpleName))
                        .limit(limit)
                        .map(name -> name.substring(name.lastIndexOf('.') + 1))
                        .collect(Collectors.joining("<")));
    }
}
