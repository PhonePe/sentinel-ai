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

package com.phonepe.sentinelai.examples.texttosql.server;

import io.dropwizard.core.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Dropwizard configuration for the embedded SQLite REST server.
 *
 * <p>This is a minimal configuration; the database path is injected programmatically when the
 * server is started by the CLI rather than read from a YAML file, so no additional fields are
 * strictly required here.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SqliteRestConfig extends Configuration {
    // No additional fields needed — the database path is set programmatically.
}
