# Component Filtering Configuration

ClojureMCP allows fine-grained control over which tools, prompts, and resources are exposed to AI assistants through configuration options in `.clojure-mcp/config.edn`. This is useful for creating focused MCP servers with only the components you need.

## Table of Contents
- [Overview](#overview)
- [Tools Filtering](#tools-filtering)
- [Prompts Filtering](#prompts-filtering)
- [Resources Filtering](#resources-filtering)
- [Examples](#examples)
- [Best Practices](#best-practices)

## Overview

Component filtering uses an allow/deny list pattern:
- **Enable lists** (`enable-*`) - When specified, ONLY these items are enabled
- **Disable lists** (`disable-*`) - Applied after enable filtering to remove specific items
- **Default behavior** - When no filtering is specified, all components are enabled

The filtering logic follows this order:
1. If an enable list is provided and empty (`[]`), nothing is enabled
2. If an enable list is provided with items, only those items are enabled
3. If no enable list is provided (`nil`), all items start enabled
4. The disable list is then applied to remove items from the enabled set

## Tools Filtering

Control which tools are available to the AI assistant.

### Configuration Keys

```edn
{:enable-tools [:clojure_eval :read_file :file_write]  ; Only these tools
 :disable-tools [:dispatch_agent :architect]}          ; Remove these tools
```

### Tool Identifiers

Tools can be specified using keywords or strings:
- `:clojure_eval` or `"clojure_eval"`
- `:read_file` or `"read_file"` 
- `:file_write` or `"file_write"`

Common tool IDs include:
- `:clojure_eval` - Evaluate Clojure code
- `:read_file` - Read file contents
- `:file_edit` - Edit files
- `:file_write` - Write files
- `:bash` - Execute shell commands
- `:grep` - Search file contents
- `:glob_files` - Find files by pattern
- `:dispatch_agent` - Launch sub-agents
- `:architect` - Technical planning
- `:code_critique` - Code review
- `:scratch_pad` - Persistent storage

### Examples

**Minimal REPL-only server:**
```edn
{:enable-tools [:clojure_eval]}
```

**Read-only exploration server:**
```edn
{:enable-tools [:read_file :grep :glob_files :LS :clojure_inspect_project]}
```

**Full access except agents:**
```edn
{:disable-tools [:dispatch_agent :architect :code_critique]}
```

## Prompts Filtering

Control which system prompts are provided to the AI assistant.

### Configuration Keys

```edn
{:enable-prompts ["clojure_repl_system_prompt" "chat-session-summarize"]
 :disable-prompts ["scratch-pad-save-as"]}
```

### Prompt Names

Prompts are identified by their string names (not keywords):
- `"clojure_repl_system_prompt"` - Main REPL interaction prompt
- `"chat-session-summarize"` - Session summarization
- `"scratch-pad-load"` - Loading scratch pad data
- `"scratch-pad-save-as"` - Saving scratch pad snapshots

### Examples

**Essential prompts only:**
```edn
{:enable-prompts ["clojure_repl_system_prompt"]}
```

**Disable scratch pad prompts:**
```edn
{:disable-prompts ["scratch-pad-load" "scratch-pad-save-as"]}
```

## Resources Filtering

Control which resource files are exposed to the AI assistant.

### Configuration Keys

```edn
{:enable-resources ["PROJECT_SUMMARY.md" "README.md"]
 :disable-resources ["CLAUDE.md" "LLM_CODE_STYLE.md"]}
```

### Resource Names

For now, resources are identified by their string names (not URIs or paths):
- `"PROJECT_SUMMARY.md"` - Project overview
- `"README.md"` - Main documentation
- `"CLAUDE.md"` - Claude-specific instructions
- `"LLM_CODE_STYLE.md"` - Coding style guide

### Examples

**Only project documentation:**
```edn
{:enable-resources ["PROJECT_SUMMARY.md" "README.md"]}
```

**Remove AI-specific resources:**
```edn
{:disable-resources ["CLAUDE.md" "LLM_CODE_STYLE.md"]}
```

## See Also

- [Model Configuration](model-configuration.md) - Configure custom LLM models
- [Tools Configuration](tools-configuration.md) - Configure tool-specific settings
- [Creating Custom MCP Servers](custom-mcp-server.md) - Build servers with custom filtering
