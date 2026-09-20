# Tripp'in AI: Multi-Agent Collaboration Protocol

This repository is actively co-engineered by two autonomous assistants:
- **AGY (Antigravity)**: Primary driver on live emulator workflows, full UI/UX flows, deterministic verification, and full-stack integration.
- **HERMES (Council / Shipped Web & Android Identity)**: Specializes in motion curves, typography floors, council critique, and web deployments.

To ensure zero stepping on each other's toes, zero uncommitted dirty states, and seamless real-time handoffs, both agents MUST follow this protocol.

---

## 1. Core Collaboration Invariants

1. **Always Commit Before Handing Off**:
   - Never leave modified files unstaged/uncommitted when inviting or yielding to the other agent.
   - Commit with explicit file paths only (`git add path/to/file`, never `git add .` or `git add -A`).
   - If work is partial or an intermediate step, commit with a descriptive message like `wip(android): ...` on a named branch or `main` so the other agent can pull/rebase without data loss.

2. **Read the Inbox at Turn Start**:
   - Inspect `.agents/STATUS.md` and `.agents/INBOX.md` to see what the other agent last completed, what branch was created, and what open requests are pending.
   - Alternatively, run `node scripts/agent-bus.mjs read` to inspect recent messages.

3. **Shared Design & Technical Vocabulary**:
   - **Unified Motion**: The canonical curve is cubic-bezier `(0.22, 1, 0.36, 1)`.
   - **Tactile Shadows**: 2.5px solid ink borders (`#18181B`) with 4px offset ink drop-shadows on cards and buttons.
   - **Palette**: Action Crimson (`#E11D48`), Carbon Ink (`#18181B`), Newsprint Cream (`#FAF8F5`).
   - **Copy Rules**: Plain hyphens or 'to' (zero em/en dashes), zero emojis, zero fake ratings or generated reviews.
   - **Haptics**: Use purposeful commit-level haptics for actions; avoid continuous handle ticks on text inputs.

4. **Message Dispatch Utility**:
   Both agents can append messages directly using `node scripts/agent-bus.mjs`:
   ```bash
   node scripts/agent-bus.mjs send --from <agy|hermes> --to <hermes|agy> --subject "<title>" --body "<details>"
   ```
   This automatically appends to `.agents/DISPATCH.jsonl` and formats `.agents/INBOX.md`.

---

## 2. Work Leases and Branching Strategy

- **Mainline**: `main` contains the verified, passing codebase.
- **Feature Branches**: If experimenting with motion or isolated component sets (e.g. `flat-android-identity`), rebase onto `main` before integration.
- **Component Locks**:
  - Before making sweeping refactors to `apps/android` or `apps/web`, post an update in `.agents/STATUS.md` under **Active Leases**.
  - When finished and verified, release the lock and commit.
