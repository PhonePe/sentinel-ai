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

package com.phonepe.sentinelai.filesystem.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void testEnsurePathCreate() {
        final Path path = tempDir.resolve("new-dir");
        final Path ensured = FileUtils.ensurePath(path.toString(), true, true);
        assertNotNull(ensured);
        assertTrue(Files.exists(ensured));
        assertTrue(Files.isDirectory(ensured));
    }

    @Test
    void testEnsurePathExisting() {
        final Path path = tempDir.resolve("existing-dir");
        FileUtils.ensurePath(path.toString(), true, true);
        final Path ensured = FileUtils.ensurePath(path.toString(), false, true);
        assertNotNull(ensured);
    }

    @Test
    void testEnsurePathIsFile() throws Exception {
        final Path path = tempDir.resolve("a-file");
        Files.writeString(path, "content");
        assertThrows(IllegalArgumentException.class, () -> FileUtils.ensurePath(path.toString(), true, true));
    }

    @Test
    void testEnsurePathNotExistsNoCreate() {
        final Path path = tempDir.resolve("not-exists");
        assertThrows(IllegalArgumentException.class, () -> FileUtils.ensurePath(path.toString(), false, true));
    }

    @Test
    void testWrite() {
        final Path path = tempDir.resolve("test-file");
        assertTrue(FileUtils.write(path, "hello".getBytes(), false));
        assertTrue(Files.exists(path));
    }
}
