package com.marcorable.ccrecorder.core.media

import org.junit.Assert.assertTrue

inline fun <reified T> assertIs(value: Any?): T {
    assertTrue("Expected ${T::class.java.name}, but was ${value?.javaClass?.name}", value is T)
    return value as T
}
