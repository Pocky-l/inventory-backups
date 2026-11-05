package com.pockyl.inventorybackups.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static {@code void (GameTestHelper)} method as a game test; {@link GameTests} registers it. Every test runs in
 * the 1x1x1 {@code inventorybackups:empty} structure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface ModTest {
    /** Ticks before the test fails. */
    int timeoutTicks() default 100;
}
