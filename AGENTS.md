# AGENTS Workflow Notes

## Purpose
This file defines a repeatable workflow for progress-log commits so the assistant can execute the full process when the user gives a short trigger phrase.

## Trigger Phrases
Use this workflow when user intent matches phrases like:
- "按流程更新进度"
- "更新项目计划并提交"
- "帮我走一遍进度提交流程"
- "同步远端后更新 md 并推送"

## Target File
- `PROJECT_PLAN.md` at repository root.

## Standard Execution Flow
1. Sync remote metadata only:
   - `git fetch origin`
2. Inspect remote delta and recent mainline context:
   - `git log --oneline --decorate HEAD..origin/main`
   - `git log --oneline --decorate -n 30`
3. Update `PROJECT_PLAN.md`:
   - Update `Last Updated` date.
   - Append one entry under `## 10. Progress Log`.
   - Entry should summarize latest project progress from remote and recent mainline history.
   - Record substantive iterative changes (`feat`/`fix`/`ci`) that materially improve functionality, reliability, observability, or quality, even when there is no large merge milestone.
4. Stage only intended docs changes:
   - `git add -- PROJECT_PLAN.md`
5. Verify staged scope is clean:
   - `git status --short`
   - Must include only `PROJECT_PLAN.md` unless user explicitly asks otherwise.
6. Commit:
   - `git commit -m "docs: update project progress log (YYYY-MM-DD)"`
7. Push:
   - `git push origin main`
8. If push is rejected (non-fast-forward):
   - `git pull --rebase --autostash origin main`
   - `git push origin main`
9. Confirm result:
   - `git show --name-status --oneline -n 1`

## Guardrails
- Never stage unrelated files by default.
- Never run destructive git commands.
- If there is a merge conflict during rebase, stop and ask user before continuing.
- If user asks to include additional files, restage explicitly and show scope before commit.

## Output Back To User
Return:
- New commit hash.
- Commit message.
- Files included.
- Whether rebase was needed.

## Completion Criteria
Workflow is complete only when:
- Commit exists locally.
- Commit is pushed to `origin/main`.
- Confirmation output is shown to user.
