# CUSTOMROM GitHub-to-ADB Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing CUSTOMROM PremiumOps app on the Galaxy S23 receive authenticated GitHub Issues, execute allowed work through the existing ADB controller against the TayTech, and post durable human-first receipts without replaying uncertain effects.

**Architecture:** Keep GitHub as a thin remote transport. Add a strict job contract, local admission/replay state, typed operation registry, small REST client/receiver and a PremiumOps integration seam that delegates all TayTech shell work to the existing `AdbRemoteController`. Preserve the current UI architecture; remote state is compact and uses existing Comandos/Terminal/Apps/session/ledger surfaces.

**Tech Stack:** Kotlin/JVM 17, Android API 29–35 runtime target / compileSdk 36, Android Views, Kadb 2.1.1, coroutines 1.10.2, HttpURLConnection, Android Keystore, JUnit 4.13.2, org.json test runtime.

**Spec:** `docs/superpowers/specs/2026-09-27-customrom-github-adb-bridge-design.md`

## Global Constraints

- Work only on `refactor/customrom-adb-s23-premium`; do not create another branch.
- `AdbRemoteController` remains the only ADB transport authority.
- Do not add a GitHub bottom-navigation destination.
- GitHub control repository defaults to private `viluadmcontas2-dot/AgentRed` with title prefix `[CUSTOMROM JOB]`.
- Job schema is `customrom.adb.job.v1`.
- Local safety classification is authoritative; Issue input cannot downgrade risk.
- GREEN may run automatically after admission.
- YELLOW requires `allowChanges=true`; before/after/rollback are retained when the typed operation supports them.
- RED remote execution is blocked.
- No token in source, Git history, logs, receipts or exported sessions.
- GitHub credentials use Android Keystore-backed encryption.
- Unknown/malformed material fields fail closed.
- Same `requestId` + same terminal contract never re-executes; different contract is a replay conflict.
- Interrupted effectful RUNNING work becomes `UNCERTAIN` and is not replayed automatically.
- Human summary precedes technical detail in receipts/UI.
- No root, flash, MCU/CAN write, firmware write or arbitrary active vehicle-control path is introduced.
- Existing automotive package protection remains authoritative.

## Review Focus

- A malicious/accidental package argument containing shell metacharacters must be rejected before command construction; Task 2 tests literal package validation.
- GitHub can return pull requests in the Issues API; Task 3 tests that PR-shaped entries are ignored.
- A GitHub receipt failure after a successful ADB effect must never re-run ADB; Task 4 tests terminal persistence before publication/retry.
- App restart with an effectful RUNNING job must produce UNCERTAIN rather than re-execute; Task 1 and Task 4 both pin this.
- A shell command whose Issue claims GREEN but local policy classifies YELLOW/RED must obey local policy; Task 2 tests local classification wins.

---

### Task 1: Unit-test foundation, job contract and replay store

**Files:**
- Modify: `apps/customrom-adb-native/app/build.gradle.kts`
- Modify: `.github/workflows/build-customrom-adb-s23-premium.yml`
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/RemoteControlModels.kt`
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/CustomromJobContract.kt`
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/IssueJobStore.kt`
- Create: `apps/customrom-adb-native/app/src/test/java/com/customrom/adb/CustomromJobContractTest.kt`
- Create: `apps/customrom-adb-native/app/src/test/java/com/customrom/adb/IssueJobStoreTest.kt`

**Interfaces:**
- Produces: `RemoteJob`, `RemoteJobMode`, `RemoteJobState`, `StoredRemoteJob`, `CustomromJobContract.parse(body: String): RemoteJob`, `CustomromJobContract.canonicalDigest(job: RemoteJob): String`.
- Produces: `IssueJobStore` with claim/running/terminal/uncertain/replay decision methods backed by an injectable file path so JVM tests use a temporary directory.

- [ ] **Step 1: Add JUnit/org.json test dependencies and a CI unit-test step**
- [ ] **Step 2: Write failing contract tests** for action/shell parse, unknown field/schema, malformed requestId, wrong target handling at admission seam, and canonical digest stability.
- [ ] **Step 3: Trigger CI and verify RED** because production contract classes do not exist.
- [ ] **Step 4: Implement minimal contract models/parser/digest**
- [ ] **Step 5: Write failing replay-store tests** for same-id same-contract reuse, same-id changed-contract conflict, RUNNING recovery to UNCERTAIN, and terminal receipt persistence.
- [ ] **Step 6: Trigger CI and verify RED** for missing store behavior.
- [ ] **Step 7: Implement minimal file-backed IssueJobStore**
- [ ] **Step 8: Run full unit suite + existing validator + Android build in CI and verify GREEN**
- [ ] **Step 9: Commit this task as one independently green slice**

### Task 2: Typed operation registry, safety gate and receipt formatting

**Files:**
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/RemoteOperationRegistry.kt`
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/RemoteReceiptFormatter.kt`
- Create: `apps/customrom-adb-native/app/src/test/java/com/customrom/adb/RemoteOperationRegistryTest.kt`
- Create: `apps/customrom-adb-native/app/src/test/java/com/customrom/adb/RemoteReceiptFormatterTest.kt`
- Reuse: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/PremiumModels.kt`

**Interfaces:**
- Consumes: `RemoteJob` from Task 1 and `PremiumSafetyPolicy.classify(command)`.
- Produces: `ResolvedRemoteOperation(title, command, risk, effectful, preflightCommand, verificationCommand, rollbackTemplate)`.
- Produces: `RemoteReceiptFormatter.format(...): String` with bounded/redacted technical output.

- [ ] **Step 1: Write failing registry tests** for diagnostics, package inspect/disable/enable, recipe lookup, invalid package metacharacters, allowChanges requirement metadata, and local safety overriding any remote claim.
- [ ] **Step 2: Verify RED in CI**
- [ ] **Step 3: Implement minimal registry using existing recipes and PremiumSafetyPolicy**
- [ ] **Step 4: Write failing receipt tests** for human-first output, stdout/stderr separation, transport failure, truncation marker, token-like redaction and rollback rendering.
- [ ] **Step 5: Verify RED in CI**
- [ ] **Step 6: Implement formatter**
- [ ] **Step 7: Run full unit suite + validator + build; verify GREEN**
- [ ] **Step 8: Commit green slice**

### Task 3: Secure GitHub configuration and REST issue client

**Files:**
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/GitHubCredentialStore.kt`
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/GitHubIssueClient.kt`
- Create: `apps/customrom-adb-native/app/src/test/java/com/customrom/adb/GitHubIssueClientTest.kt`
- Modify: `apps/customrom-adb-native/app/src/main/AndroidManifest.xml` only if an additional platform permission is actually required; INTERNET already exists.

**Interfaces:**
- Produces: `GitHubControlConfig(owner, repo, allowedAuthor, target, titlePrefix, pollSeconds, enabled)`.
- Produces: Keystore-backed `GitHubCredentialStore.saveToken/loadToken/clearToken`.
- Produces: `GitHubIssueClient.listOpenJobs()`, `comment(issueNumber, body)`, `close(issueNumber)` and parsed `RemoteGitHubIssue`.

- [ ] **Step 1: Write failing client parsing/admission-shape tests** for Issue JSON, PR-shaped item ignored, missing body, author extraction and title-prefix filtering helper.
- [ ] **Step 2: Verify RED in CI**
- [ ] **Step 3: Implement REST client with HttpURLConnection and bounded bodies/timeouts**
- [ ] **Step 4: Implement Android Keystore AES/GCM token storage without exposing token to logs**
- [ ] **Step 5: Run unit suite + validator + build; verify GREEN**
- [ ] **Step 6: Commit green slice**

### Task 4: Receiver/coordinator and no-replay execution semantics

**Files:**
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/GitHubIssueReceiver.kt`
- Create: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/RemoteJobCoordinator.kt`
- Create: `apps/customrom-adb-native/app/src/test/java/com/customrom/adb/RemoteJobCoordinatorTest.kt`
- Create: `apps/customrom-adb-native/app/src/test/java/com/customrom/adb/RemoteAdmissionPolicyTest.kt`

**Interfaces:**
- Consumes Tasks 1–3.
- Produces: one-job-at-a-time polling receiver while PremiumOps is alive.
- Produces: coordinator seam with injectable command executor and receipt publisher for JVM tests.
- Produces: admission result validating repository/title/author/schema/target before claim.

- [ ] **Step 1: Write failing admission tests** for wrong author, wrong target, unrelated prefix and valid owner job.
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement admission/receiver discovery without ADB execution inside receiver**
- [ ] **Step 4: Write failing coordinator tests** for GREEN execution, YELLOW rejected without allowChanges, RED blocked, RUNNING persisted before effect, terminal persisted before publish, receipt retry without ADB replay, transport error vs command error, and interrupted effectful job becoming UNCERTAIN.
- [ ] **Step 5: Verify RED**
- [ ] **Step 6: Implement minimal coordinator**
- [ ] **Step 7: Run full unit suite + validator + build; verify GREEN**
- [ ] **Step 8: Commit green slice**

### Task 5: PremiumOps integration and human-first remote UX

**Files:**
- Modify: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/PremiumOpsActivity.kt`
- Modify: `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/PremiumOpsModels.kt` only if shared presentation state belongs there.
- Modify: `tools/validate_native_customrom.py`
- Create or modify focused JVM/source-contract tests if logic can be extracted from Activity instead of testing private Android Views.

**Interfaces:**
- Consumes all prior tasks.
- Produces: receiver start/stop with Activity lifecycle, compact remote status in the existing top bar, setup dialog, remote execution provenance in session/evidence, shared Apps/Terminal behavior and one-tap disable of remote control.

- [ ] **Step 1: Add a failing source/contract test** proving no new GitHub nav destination and requiring compact Remote status/setup hook.
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Integrate receiver/coordinator with existing AdbRemoteController and active-operation serialization**
- [ ] **Step 4: Add compact Remote status and setup dialog** using defaults AgentRed / [CUSTOMROM JOB] / taytech-primary, with token field handled only by the credential store.
- [ ] **Step 5: Route remote shell output into existing session/technical evidence and package actions into ChangeLedger where applicable**
- [ ] **Step 6: Extend validator for required remote-control classes and anti-secret/anti-nav invariants**
- [ ] **Step 7: Run full unit suite + validator + Android build; verify GREEN**
- [ ] **Step 8: Commit green slice**

### Task 6: End-to-end software verification and release evidence

**Files:**
- Modify: `PROJECT_STATE.md` only with verified software-side state and explicit physical-validation status.
- Modify: CI proof metadata only through existing workflow behavior when applicable.

**Interfaces:**
- Consumes complete integrated branch.
- Produces: fresh CI evidence for tests, validation, build and artifact; no claim of physical S23→TayTech behavior until physically observed.

- [ ] **Step 1: Re-resolve remote HEAD and inspect final diff against the design baseline**
- [ ] **Step 2: Run/observe fresh full CI on that HEAD**: unit tests, native validator, assembleDebug and artifact creation.
- [ ] **Step 3: Inspect workflow logs/proof and artifact metadata; no completion claim from commit presence alone**
- [ ] **Step 4: Update PROJECT_STATE with exact verified SHA/run and mark physical installation/control test PENDING unless real-device evidence exists**
- [ ] **Step 5: Perform whole-branch code-verification review against spec + Review Focus**
- [ ] **Step 6: Fix Critical/Important findings using RED→GREEN tests in one pass; defer Minor findings explicitly**
- [ ] **Step 7: Run fresh final CI after any fix and record final software verdict**

## Execution decision

Owner explicitly instructed on 2026-09-27: approve the spec and execute continuously until closed. Execution method is therefore **Native/inline** under `superpowers:executing-plans`; do not stop for per-task confirmation. The only unavoidable external gate is real-device S23/TayTech installation/physical validation, which must be reported as pending rather than fabricated if no device execution surface is available.
