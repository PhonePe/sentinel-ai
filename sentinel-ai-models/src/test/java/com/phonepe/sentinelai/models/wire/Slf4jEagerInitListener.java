/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.models.wire;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.LoggerFactory;

/**
 * Initializes the SLF4J binding once, single-threaded, before the first test class executes.
 * <p>
 * Under JUnit parallel class execution the one-time {@link LoggerFactory} binding races with
 * the first tests that use loggers: while one worker thread runs the binding (ServiceLoader
 * scan plus provider {@code initialize()}), sibling classes calling {@code getLogger()} see
 * the {@code ONGOING_INITIALIZATION} state and receive {@code SubstituteLogger} instances,
 * which breaks tests that cast the logger to a logback {@code Logger} (for example
 * {@link WirePayloadLoggerTest}). Opening the session happens strictly before any test
 * executes, so this listener closes that window for the whole module.
 * </p>
 * Registered through {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}.
 */
public class Slf4jEagerInitListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(final LauncherSession session) {
        LoggerFactory.getLogger(Slf4jEagerInitListener.class);
    }
}
