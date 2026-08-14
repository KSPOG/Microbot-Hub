# KSP Auto Repair Agent

KSP Auto Repair Agent is a continuous diagnostic and repair bridge for KSP Microbot plugins.

## What it does

1. Watches active KSP plugins while the client is logged in.
2. Keeps a rolling runtime trace containing player location, animation, interaction target, inventory state, XP state, `Microbot.status`, and active KSP plugin classes.
3. Tails `~/.runelite/logs/client.log` for KSP errors and source-loader revisions.
4. Detects silent stalls when meaningful runtime state stops changing for the configured threshold.
5. Creates an incident bundle under `~/.runelite/ksp-agent-repair/incidents/` with the trace, recent log lines, reflected plugin/script state, and an optional client screenshot.
6. Synchronizes a managed clone of the repository consumed by KSP Source Loader.
7. Invokes the configured coding agent non-interactively.
8. Rejects agent edits outside the failing plugin's Java source directory.
9. Compiles the full KSP source repository before allowing a commit.
10. Commits and pushes a successful candidate when `Push validated fixes` is enabled.
11. Watches KSP Source Loader for the pushed revision.
12. Validates runtime progress after that revision loads and automatically reverts a source-loader-rejected repair.

## Default source repository

`https://github.com/KSPOG/ksppluginsrelease.git`

The managed clone defaults to:

`%USERPROFILE%\.runelite\ksp-agent-repair\worktree\ksppluginsrelease`

## Agent backends

### Claude Code

The built-in backend calls the configured `Agent executable` in non-interactive mode and restricts the agent to source-reading/editing tools. Git operations remain owned by the repair bridge.

The executable must already be installed and authenticated on the machine running Microbot.

### Custom command

Select `Custom command` to use another local coding-agent launcher. The command template supports:

- `{repo}` - managed KSP source repository path
- `{prompt}` - generated incident prompt file

The custom process must edit files in the managed repository and exit when finished. The repair bridge performs guardrail validation, compilation, commit, push, and rollback.

## Important settings

- `Full auto repair`: enables the diagnose/edit/compile/push loop.
- `Stall threshold (sec)`: default 120 seconds.
- `Incident cooldown (sec)`: default 180 seconds.
- `Post-fix validation (sec)`: default 60 seconds after the source loader observes the repaired revision.
- `Agent attempts`: default 3 compile/edit attempts per incident.
- `Capture screenshots`: includes the RuneLite canvas in incident evidence.
- `Push validated fixes`: allows the bridge to commit and push a locally compiling repair.

## Guardrails

The coding agent is not allowed to modify the repair bridge, KSP Source Loader, credentials, build infrastructure, or files outside the target plugin's Java source directory. Non-Java changes and renames are rejected. The bridge, not the coding agent, owns Git commit/push/reset/revert operations.

## Build

From the Microbot-Hub repository root:

```powershell
./gradlew build -PpluginList=KspAutoRepairPlugin
```

## Runtime prerequisites

For full autonomous repair, the Microbot machine needs:

- Git available on `PATH` and authenticated for push access to the configured source repository.
- A configured coding-agent executable that can run without interactive prompts.
- KSP Source Loader enabled and watching the same repository/branch as this plugin.

If any of these prerequisites are missing, incidents are still written to disk but the autonomous repair stage cannot complete.
