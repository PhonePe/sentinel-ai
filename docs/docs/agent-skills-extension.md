---
title: Agent Skills Extension
description: Build modular, reusable skills for agents in Sentinel AI
---

# Agent Skills Extension

The **Agent Skills Extension** enables agents to discover, activate, and use modular skills loaded from the filesystem. Skills are self-contained, reusable capabilities that can be discovered at startup and loaded on-demand, providing agents with extensible functionality without modifying agent code.

## What Are Agent Skills?

Agent Skills are **self-contained bundles** that provide specialized instructions, tools, references, and utilities to an agent. Unlike tools (which execute logic), skills provide **guidance and context** that helps agents understand when and how to perform specialized tasks.

### Skill Components

Each skill is a directory containing:

- **`SKILL.md`** (required): Contains YAML frontmatter metadata and Markdown instructions
- **`references/`** (optional): Documentation files agents can read for deeper context
- **`scripts/`** (optional): Executable scripts agents can invoke via tools
- **`assets/`** (optional): Static files (images, data, configs) the agent can reference

### Skills vs. Tools

| Aspect | Tools | Skills |
|--------|-------|--------|
| **Purpose** | Execute logic and perform actions | Provide guidance and context |
| **Invocation** | Called directly by the agent | Activated by the agent, then followed |
| **Storage** | Code-based (Java classes) | Filesystem-based (Markdown + assets) |
| **Scope** | Task execution | Task understanding and planning |
| **Example** | `readFile()`, `callAPI()` | "How to write secure code", "Data validation guide" |

## How Skills Work

The Agent Skills Extension uses a **two-tier loading mechanism** for efficiency:

### Tier 1: Discovery (Startup)
When the extension is initialized, only **metadata** is loaded from all skill directories:
- Skill name and description
- Metadata key-value pairs
- Compatibility information

This is lightweight and allows the extension to build a **skills catalog** without loading large instruction sets into memory.

### Tier 2: Loading (On-Demand)
When the agent calls `activateSkill(skillName)`, the full skill is loaded:
- Complete Markdown instructions
- Reference files (mapped to paths)
- Script files (mapped to paths)
- Asset files (mapped to paths)

## Modes of Operation

The Agent Skills Extension supports two modes:

### 1. Multi-Skill Mode
Discovery and activation of multiple skills from one or more directories.

**Setup**:
```java
final var extension = AgentSkillsExtension.withMultipleSkills()
    .baseDir("/path/to/base")
    .skillsDirectories(List.of(
        "./skills",
        "/absolute/path/to/other/skills"
    ))
    .skillsToLoad(null)  // Null = discover all; or pass specific names
    .build();
```

**Tools Registered**:

- `agent_skills_extension_list_skills` — List all available skills
- `agent_skills_extension_activate_skill` — Activate a specific skill
- `agent_skills_extension_read_skill_reference` — Read reference files from loaded skills

**Behavior**:

- System prompt injects a skills discovery task that encourages the agent to review available skills
- If a skill matches the user's request, the agent activates it and follows the instructions

### 2. Single-Skill Mode
Load a single skill directly, with its instructions injected into the system prompt.

**Setup**:
```java
final var extension = AgentSkillsExtension.withSingleSkill()
    .baseDir("/path/to/base")
    .singleSkill("/path/to/skill/directory")
    .build();
```

**Tools Registered**:

- `agent_skills_extension_read_skill_reference` — Only tool available for reference reading

**Behavior**:

- Skill instructions are directly injected into the system prompt
- No skill discovery or activation—the skill is always active
- Ideal for specialized agents that focus on a single domain

## Creating a Skill

### Skill Directory Structure

```
my-skill/
├── SKILL.md                 # Skill metadata + instructions (required)
├── references/              # Reference documentation (optional)
│   ├── guide.md
│   └── examples.md
├── scripts/                 # Executable scripts (optional)
│   └── setup.sh
└── assets/                  # Static assets (optional)
    ├── config.json
    └── template.txt
```

### Writing SKILL.md

The `SKILL.md` file has two parts: **YAML frontmatter** and **Markdown instructions**.

#### Frontmatter (YAML)

Required fields:

```yaml
---
name: my-skill
description: A brief description of what this skill does and when to use it (max 1024 chars)
---
```

Optional fields:

```yaml
---
name: my-skill
description: A skill for code review and quality assurance
license: Apache-2.0
compatibility: Requires Java 17+, no external dependencies
allowed-tools: bash-runner api-caller  # Space-delimited list of pre-approved tools
metadata:
  author: Your Name
  version: "1.0"
  tags:
    - code-review
    - quality
---
```

**Validation**:
- `name` must match the directory name (e.g., `my-skill/` contains `name: my-skill`)
- `name` must be 1-64 characters, lowercase alphanumeric with hyphens
- `description` is displayed in the skills catalog and used for skill discovery

#### Instructions (Markdown)

After the closing `---`, provide detailed instructions in Markdown:

```markdown
---
name: code-review-skill
description: Conduct thorough code reviews and provide quality feedback
---

# Code Review Skill

## When to Use This Skill
Use this skill when:
- You need to review code for quality, security, or performance
- You're checking for best practices and coding standards
- You're validating error handling and edge cases

## Instructions

### Step 1: Understand the Context
Before reviewing, gather:
- The purpose of the code
- The target audience (internal tools, public API, etc.)
- Any specific standards or guidelines to follow

### Step 2: Conduct the Review
Examine the code for:
- **Correctness**: Does it do what it's supposed to do?
- **Clarity**: Is the code readable and maintainable?
- **Performance**: Are there obvious inefficiencies?
- **Security**: Are there potential vulnerabilities?
- **Tests**: Is the code properly tested?

### Step 3: Provide Feedback
Format feedback clearly:
- **Issue**: What's the problem?
- **Severity**: Critical, Major, Minor, or Suggestion
- **Suggestion**: How to fix or improve it

## Reference Documents
See `references/security-checklist.md` for a detailed security review checklist.
```

### Example Skill

Here's a complete example:

```markdown
---
name: data-validation-skill
description: Validate and sanitize data according to best practices
license: Apache-2.0
metadata:
  version: "1.0"
  author: data-team
---

# Data Validation Skill

## Overview
This skill provides comprehensive guidance for validating and sanitizing data in your applications.

## When to Use
- User input validation (forms, APIs, file uploads)
- Data transformation and conversion
- Cross-system data synchronization
- Database record validation

## Key Principles

### 1. Whitelist, Don't Blacklist
Define what is **allowed**, not what is **forbidden**. This is more secure and maintainable.

### 2. Validate Early, Fail Fast
Validate data at the entry point. Don't propagate invalid data through your system.

### 3. Provide Clear Error Messages
When validation fails, tell users exactly what's wrong and how to fix it.

### 4. Type Safety First
Use strong typing to prevent type-related bugs.

## Validation Checklist

- [ ] Is the data type correct?
- [ ] Is the value within acceptable ranges?
- [ ] Are required fields present?
- [ ] Does the data match the expected format?
- [ ] Are special characters properly escaped?
- [ ] Is the data normalized (e.g., trimmed, lowercased)?

## Common Patterns

### Email Validation
```
Format: local-part@domain
Constraints: Max 254 chars, specific characters allowed
Always verify with a confirmation email
```

### URL Validation
```
Format: scheme://authority/path?query#fragment
Constraints: Valid scheme, authority, path structure
Always validate and sanitize query parameters
```

## Reference Documents
For detailed validation rules by data type, see `references/validation-rules.md`.
For code examples, see `references/code-examples.md`.
```

### Reference Files

Reference files in the `references/` directory provide detailed information agents can read:

```markdown
# references/validation-rules.md

## Email Validation Rules

- Max length: 254 characters
- Valid characters: A-Z, a-z, 0-9, !, #, $, %, &, ', *, +, -, /, =, ?, ^, _, `, {, |, }, ~, .
- At least one @ symbol
- Domain must be valid

## Phone Number Rules

- Formats: +1-234-567-8900, (234) 567-8900, 234-567-8900
- At least 10 digits
- Country code optional but recommended
```

### Scripts

Scripts in the `scripts/` directory can be invoked by agents via tools:

```bash
#!/bin/bash
# scripts/validate-config.sh
# Validates a configuration file against a schema

config_file="$1"
schema_file="$2"

if [ ! -f "$config_file" ]; then
  echo "Error: Config file not found: $config_file"
  exit 1
fi

# Validation logic here
echo "Config validation passed"
```

## Integration with Agents

### Creating an Agent with Skills

```java
public class MyAgent extends Agent<String, String, MyAgent> {

    public MyAgent(@NonNull AgentSetup setup) {
        super(String.class,
              "You are a helpful assistant with access to specialized skills.",
              setup,
              List.of(),
              Map.of());
    }

    @Override
    public String name() {
        return "my-agent";
    }
}

// Setup the extension
final var skillsExtension = AgentSkillsExtension.withMultipleSkills()
    .baseDir(System.getProperty("user.home"))
    .skillsDirectories(List.of("./skills", "./custom-skills"))
    .skillsToLoad(null)  // Discover all skills
    .build();

// Create agent with extension
final var agent = new MyAgent(agentSetup);
final var agentWithSkills = agent.registerExtension(skillsExtension);
```

### Using Skills in an Agent

When an agent with the skills extension runs:

1. **Discovery Task**: The agent receives a secondary task to check for relevant skills
2. **Skill Activation**: If a skill is relevant, the agent calls `activateSkill(skillName)`
3. **Following Instructions**: The agent reads the skill's instructions and follows them
4. **Reference Lookup**: The agent can call `readSkillReference(skillName, filePath)` for detailed information

Example interaction:

```
User: "I need to write code that validates user email addresses."

Agent:
1. Checks available skills via listSkills()
2. Sees "data-validation-skill" is available
3. Activates the skill: activateSkill("data-validation-skill")
4. Reads the skill instructions and best practices
5. Optionally reads a reference: readSkillReference("data-validation-skill", "validation-rules.md")
6. Provides the user with comprehensive guidance on email validation
```

## Tools Provided by the Extension

### `listSkills()`

**Description**: List all available skills with their descriptions.

**Parameters**: None

**Returns**: A formatted markdown catalog of available skills.

**Example**:
```
Available Skills:

- **data-validation-skill**: Validate and sanitize data according to best practices
- **code-review-skill**: Conduct thorough code reviews and provide quality feedback
- **api-design-skill**: Design RESTful APIs with best practices
```

### `activateSkill(skillName: String)`

**Description**: Activate a skill and get its full instructions.

**Parameters**:
- `skillName` (String): The name of the skill to activate (e.g., "data-validation-skill")

**Returns**: 
- Full skill instructions (Markdown)
- List of available reference files
- List of available scripts
- List of available assets

**Example Response**:
```
# Skill Activated: data-validation-skill

[Full markdown instructions...]

## Available Reference Files
- validation-rules.md
- code-examples.md

## Available Scripts
- validate-config.sh

## Available Assets
- schema.json
```

### `readSkillReference(skillName: String, referenceFile: String)`

**Description**: Read a reference file from an activated skill.

**Parameters**:
- `skillName` (String): The name of the skill
- `referenceFile` (String): Path relative to the `references/` directory

**Returns**: Content of the reference file as plain text.

**Example**:
```java
// After activating the skill, read a reference
readSkillReference("data-validation-skill", "validation-rules.md")
```

**Notes**:
- The skill must be activated before you can read its references
- Use forward slashes for nested paths: `subdir/file.md`
- Returns error if the skill or reference file doesn't exist

## Best Practices

### Skill Design

1. **Clear, Specific Purpose**: Each skill should have one clear purpose. Don't mix unrelated capabilities.

2. **Comprehensive Instructions**: Provide step-by-step guidance that doesn't assume deep knowledge.

3. **Use References for Depth**: Put detailed reference material in `references/` to keep the main instructions concise.

4. **Versioning**: Include a version in metadata and document breaking changes.

5. **Examples**: Use markdown code blocks to provide concrete examples.

### Skill Organization

1. **Descriptive Names**: Use kebab-case names that describe the skill's purpose (e.g., `security-audit-skill`, not `util`).

2. **Consistent Metadata**: Always include name, description, and author in frontmatter.

3. **Reference Organization**: Group related references in subdirectories if there are many.

### Agent Configuration

1. **Multi-Skill for General Agents**: Use multi-skill mode for general-purpose agents that handle diverse tasks.

2. **Single-Skill for Specialized Agents**: Use single-skill mode for domain-specific agents (e.g., a code-review agent).

3. **Filtered Discovery**: Use `skillsToLoad` to limit discovery to only relevant skills, improving prompt efficiency.

## Limitations and Considerations

1. **Filesystem-Based**: Skills must be stored on the filesystem. Distributed skill loading (e.g., from S3) would require custom implementation.

2. **Directory Validation**: The skill directory name must match the `name` field in the YAML frontmatter. This is validated at discovery time.

3. **Reference Size**: Large reference files increase prompt context when read. Keep references focused and practical.

4. **No Nesting**: Skills must be direct children of skill directories. Nested skill directories are not discovered.

5. **Lazy Loading**: Skills are loaded on-demand, so there's no validation of reference file integrity until the skill is activated.

## Troubleshooting

### Skill Not Found During Discovery

**Symptom**: Skill doesn't appear in `listSkills()` output.

**Cause**: 
- Directory doesn't exist at the specified path
- `SKILL.md` is missing or malformed
- Skill name doesn't match directory name

**Solution**:
```bash
# Verify directory exists
ls -la /path/to/skills/my-skill/

# Verify SKILL.md exists and is valid
cat /path/to/skills/my-skill/SKILL.md

# Ensure name in frontmatter matches directory
grep "^name:" /path/to/skills/my-skill/SKILL.md
```

### Reference File Not Found

**Symptom**: `readSkillReference()` returns an error.

**Cause**:
- Skill hasn't been activated yet
- Reference file path is incorrect
- File doesn't exist in the `references/` directory

**Solution**:
1. Call `activateSkill()` first
2. Check available references in the activation response
3. Use exact filename/path from the references list

### Path Resolution Issues

**Symptom**: "Skills directory does not exist" error.

**Cause**: The path resolution order is:
1. Path as-is (if it's an existing directory)
2. Relative to `baseDir`
3. Relative to current working directory

**Solution**: Use absolute paths or ensure relative paths are correct from the agent's working directory.

## Example Use Cases

### Use Case 1: Code Review Agent

```java
// Single-skill mode: focus on code review
final var extension = AgentSkillsExtension.withSingleSkill()
    .baseDir("/path/to/skills")
    .singleSkill("/path/to/skills/code-review-skill")
    .build();

final var agent = new CodeReviewAgent(setup)
    .registerExtension(extension);
```

### Use Case 2: Multi-Purpose Assistant

```java
// Multi-skill mode: discover multiple skills
final var extension = AgentSkillsExtension.withMultipleSkills()
    .baseDir(System.getProperty("user.dir"))
    .skillsDirectories(List.of(
        "./company-skills",
        "./open-source-skills"
    ))
    .skillsToLoad(null)  // Discover all
    .build();

final var agent = new GeneralAssistant(setup)
    .registerExtension(extension);
```

### Use Case 3: Filtered Skill Discovery

```java
// Discover only specific skills to reduce prompt context
final var extension = AgentSkillsExtension.withMultipleSkills()
    .baseDir("/skills")
    .skillsDirectories(List.of("./database-skills"))
    .skillsToLoad(List.of(
        "schema-design-skill",
        "query-optimization-skill"
    ))
    .build();

final var agent = new DatabaseArchitectAgent(setup)
    .registerExtension(extension);
```

## See Also

- [Agent Memory Extension](agent-memory.md) — For persistent knowledge across sessions
- [Using Tools](tools.md) — For executable tools (different from skills)
- [Agents](agents.md) — Core agent implementation
