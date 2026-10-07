<!-- project-brain:begin v1 managed block - do not edit inside this region -->
## Project Brain (durable project memory)

This project uses Project Brain, a local store of durable project knowledge. It is not a
service; nothing leaves this machine.

**At the start of a session, or after a context break:** call `get_project_context` once.

**Before asking the user something:** call `search_memory`. The answer may already be recorded.

**Save with `save_memory` when you learn something durable:**
- a decision, and — in `why` — the reason for it, including what you rejected
- a bug and its fix, with the commit if you know it
- a task, with its current state
- a reference worth keeping

**Do not save:** conversation, tool output, file contents, or anything the code or `git log`
already records. If it is not in the repository and a future session would need a human to
explain it, it belongs here.

**Correcting an earlier decision:** do not edit the old memory. Save a new one with
`supersedes` set to the old memory's id, so the history stays intact.

**Before ending a work session:** call `save_session_summary` once — what changed, what was
decided and why, what is still open.

Full tool reference: run `pb doctor` or `pb status --json`.
<!-- project-brain:end -->
