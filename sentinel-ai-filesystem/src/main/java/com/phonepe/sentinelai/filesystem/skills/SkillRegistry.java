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

package com.phonepe.sentinelai.filesystem.skills;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Registry contract for discovering and loading agent skills.
 */
public interface SkillRegistry {

    void discoverSkills(String baseDir, List<String> skillsDirs, Collection<String> skillsToLoad) throws IOException;

    void discoverSkills(Path skillsDirectory, Set<String> skillsToLoad) throws IOException;

    Map<String, String> getSkillCatalog();

    Set<String> getSkillNames();

    Optional<String> loadReferenceFile(String skillName, String referenceFilePath) throws IOException;

    Optional<AgentSkill> loadSkill(String skillName);

    Optional<AgentSkill> loadSkillFromPath(Path skillPath) throws IOException;
}
