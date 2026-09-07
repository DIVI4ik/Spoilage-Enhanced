package com.spoilageenhanced.util;

public class RandomizableContainerHelper {
    public static final ThreadLocal<Boolean> IS_RANDOMIZING = ThreadLocal.withInitial(() -> Boolean.FALSE);
}
