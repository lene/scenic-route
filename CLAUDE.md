# Project System Instructions

## 1. Session Bootstrap (Continuity Across Sessions and Machines)

### At the start of every session, before doing anything else:
1. Read `SPEC.md` — the authoritative plan: mission, phases, locked decisions.
2. Read `PROGRESS.md` — current phase, what's done, what's in flight, and the single next step. This is where you resume.
3. Read `DECISIONS.md` and `JOURNAL.md` — chosen directions and their rationale, plus learnings, dead ends, and gotchas from prior sessions. Do not re-tread an approach the journal records as already tried and abandoned.
4. Check `git log --oneline` and `git tag` — tags `phase-N-complete` mark completed phases; the log shows what has actually landed.
5. Run the full test suite. It must be green and consistent with what `PROGRESS.md` claims. **If the tests fail, or reality contradicts `PROGRESS.md`, stop and surface it — do not start new work on a broken or inconsistent tree.**
6. Resume at the single next step named in `PROGRESS.md`. If that step is "awaiting sign-off at Checkpoint N," do not proceed past it — wait for the human.

### Keep the state current (so the next bootstrap works):
* **`PROGRESS.md`** is overwritten as the closing step of each working increment: update the current phase, the next step, and anything blocked.
* **`JOURNAL.md`** is append-only: whenever you learn something non-obvious, hit a dead end, or reject an approach, append a dated line before ending the session.
* **`DECISIONS.md`** records each non-trivial decision with a one-line rationale.
* At each checkpoint: tag the commit `phase-N-complete`, update `PROGRESS.md`, append any learnings to `JOURNAL.md`, then wait for sign-off.
* If a learning lives only in this conversation, it is lost on the next checkout. Write it down.

## 2. Core Principles and Priorities
* Code quality, functional purity, and absolute correctness hold top priority over delivery speed.
* Adopt a functional programming paradigm wherever appropriate.
* Maintain immutability by default; prefer `val` over `var`, and immutable collections over mutable ones.
* Avoid exceptions for control flow or expected errors; use functional error handling constructs (`Option`, `Either`, `Try`).
* Eliminate `null` values entirely; rely on `Option`.
* Ensure exhaustive pattern matching to leverage the compiler's safety guarantees.

## 3. Testing & Test-Driven Development (TDD)
* Employ Test-Driven Development (TDD) principles: write failing tests before implementing feature logic.
* Ensure high test coverage for core domain logic and critical paths.
* Separate unit tests from integration tests logically and in the directory structure.
* Keep test files focused; use descriptive test names that document the expected behavior.

## 4. Strict Compiler & Linter Constraints
* Utilize **WartRemover** to enforce functional purity at compile-time.
* Configure CI to fail on Wart warnings. Code must be free of unsafe warts (e.g., `var`, `null`, `return`, `Any`, and unhandled Exceptions) unless explicitly and carefully suppressed with a documented justification.
* Use `scalafix` and `scalafmt` in tandem with WartRemover; Scalafmt handles syntax formatting, Scalafix handles semantic refactoring, and WartRemover guarantees compile-time functional strictness.

## 5. Workflow, Git Hygiene, and CI
* Never commit or push directly to the `main` branch. All work must happen in feature branches.
* Ensure branch names are descriptive and linked to an issue or feature ticket.
* Run the test suite, code formatter (`scalafmt`), and linter (`scalafix`) via a Git `pre-commit` or `pre-push` hook before integrating changes.
* A thorough CI pipeline (e.g., GitHub Actions) is the final gatekeeper; it must pass all tests, style checks, and security scans before a pull request can be merged.

## 6. Execution and Long-Running Commands
* When executing long-running terminal commands (like builds, test suites, or heavy dependency fetches), always pipe the output using `tee` to a temporary file.
* Example syntax: `sbt clean test | tee /tmp/sbt_test_run.log`
* This ensures that if the process fails or scrollback is lost, the output can be examined directly without re-running the heavy computation.

## 7. Documentation Management
* Code and documentation must be kept in absolute synchronization. Any architectural or behavioral code change requires a corresponding documentation update in the same commit.
* Maintain user-facing documentation in the `docs/` directory.
* Maintain developer and architectural documentation in the `docs/arc42/` directory, strictly following the Arc42 template structure.
* Document "why" in the code comments, not "what". Let the code explain the "what".

## 8. AI Assistant Guardrails
* Read the codebase context thoroughly before suggesting file modifications.
* Do not make assumptions about ambiguous requirements or missing project contexts; ask for clarification first.
* Produce complete, functional code blocks. Do not use placeholders like `// ... rest of the code ...` unless explicitly instructed to provide a snippet.
* If a suggested change breaks an existing test, update the code to pass the test, rather than modifying the test to fit the broken code (unless the requirement itself has fundamentally changed).
