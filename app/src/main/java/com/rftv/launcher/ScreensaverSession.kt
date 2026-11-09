package com.rftv.launcher

import java.util.concurrent.atomic.AtomicBoolean

/** Prevents the manual Activity and Android DreamService from showing at once. */
internal object ScreensaverSession {
    private val active = AtomicBoolean(false)

    fun tryAcquire(): Boolean = active.compareAndSet(false, true)

    fun release() {
        active.set(false)
    }
}
