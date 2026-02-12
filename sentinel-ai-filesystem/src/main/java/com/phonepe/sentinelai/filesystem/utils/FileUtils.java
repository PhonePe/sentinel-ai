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


import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@UtilityClass
@Slf4j
public class FileUtils {

    /**
     * Ensures that the provided path exists and is a directory with the required permissions. If the path does not
     * exist and createIfNotExists is true, it will attempt to create the directory.
     *
     * @param path              The path to check or create.
     * @param createIfNotExists Whether to create the directory if it does not exist.
     * @param writeCheck        Whether to check for write permissions on the directory.
     * @return The absolute, normalized Path object representing the directory.
     * @throws IllegalArgumentException If the path is invalid, does not have the required permissions, or cannot be
     *                                  created when requested.
     */
    public static Path ensurePath(String path, boolean createIfNotExists, boolean writeCheck) {
        final var absolutePath = Path.of(path).toAbsolutePath().normalize();
        if (Files.exists(absolutePath)) {
            if (!Files.isDirectory(absolutePath) || !Files.isReadable(absolutePath) || (writeCheck && !Files.isWritable(
                                                                                                                        absolutePath))) {
                throw new IllegalArgumentException("Sanity check for %s Failed. Please check it exists and has the required permissions"
                        .formatted(absolutePath));
            }
        }
        else if (createIfNotExists) {
            try {
                Files.createDirectories(absolutePath);
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to create directory: " + absolutePath, e);
            }
        }
        else {
            throw new IllegalArgumentException("Provided path does not exist: " + absolutePath);
        }
        if (!Files.isDirectory(absolutePath) || !Files.isReadable(absolutePath) || !Files.isWritable(absolutePath)) {
            throw new IllegalArgumentException("Provided path is not a directory: " + absolutePath);
        }
        return absolutePath;
    }

    /**
     * Writes data to a file atomically. It first writes to a temporary file and then moves it to the target location.
     * This ensures that the file is not left in a corrupted state if the write operation is interrupted.
     *
     * @param filePath The path of the file to write to.
     * @param data     The byte array data to write.
     * @param append   Whether to append to the existing file or overwrite it.
     * @return true if the write operation was successful, false otherwise.
     */
    @SneakyThrows
    public static boolean write(Path filePath, byte[] data, boolean append) {
        final StandardOpenOption[] options = append ? new StandardOpenOption[]{
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
        } : new StandardOpenOption[]{
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
        };
        Files.write(filePath, data, options);
        return true;
    }

}
