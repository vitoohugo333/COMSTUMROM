# CUSTOMROM — GitHub Issues → S23 → ADB → TayTech

**Data:** 2026-09-27  
**Status:** DESIGN APPROVED / PRE-IMPLEMENTATION  
**Repository:** vitoohugo333/COMSTUMROM  
**Work line:** refactor/customrom-adb-s23-premium  
**Design baseline observed before this spec:** aa96a731befd8433c080281bdf9963f5dde39ad4

## 1. Objective

Enable ChatGPT/the owner to interact with the TayTech remotely through the already-existing CUSTOMROM app on the Galaxy S23.

The topology is deliberately simple:

    ChatGPT / owner
          ↓
    private GitHub Issue
          ↓
    CUSTOMROM on Galaxy S23
          ↓
    existing Kadb / ADB transport
          ↓
    TayTech multimedia
          ↓
    result / evidence
          ↓
    GitHub Issue receipt

The S23 is the controller/bridge. TayTech remains the ADB target.

This feature must make it practical to inspect the Android system; run diagnostics; inspect processes, packages, services, settings, files, sockets and logs; understand how OEM/automotive components are connected; test performance hypotheses; stop applications temporarily; disable applications reversibly when authorized; restore previous state; change safe/reversible Android settings; execute advanced ADB shell commands when a typed action does not yet exist; and preserve evidence of what was requested, executed and observed.

The control transport is GitHub Issues. It is not a new backend platform.

## 2. Product principle

The feature follows the Notion contracts:

- Blueprint Premium UI/UX — Método CUSTOMROM reutilizável;
- Método aplicado — Omega Dev 4.0 Premium UI/UX;
- AUDITORIA FINAL PRÉ-IMPLEMENTAÇÃO — CUSTOMROM + OMEGA DEV + BASELINE UI;
- Blueprint Car Info Next — Método OMEGAS + CUSTOMROM.

Binding UX rule:

> The interface exposes human intent, current state, consequence and recovery. GitHub, Kadb, mDNS, package-manager syntax and transport details stay below the primary surface unless they are useful for diagnosis.

Therefore this feature MUST NOT create a primary GitHub destination merely because GitHub became a transport.

Existing primary navigation remains centered on human work:

    Comandos | Terminal | Apps

GitHub Issues are infrastructure.

## 3. Non-goals

This slice does not port AgentRed into Android, add a general-purpose mission scheduler, add dozens of workers, add a server/database/cloud backend, add an embedded LLM, replace PremiumOps, replace AdbRemoteController, rewrite Kadb connection/pairing/reconnection, add root, flash Android/MCU/CAN firmware, transmit arbitrary CAN frames, write MCU/vehicle protocol state, or remove protected automotive components automatically.

AgentRed is used only as a reference for durable remote-control principles: strict contract, authenticated issuer, request identity, no blind replay and receipts.

## 4. Source and control authority

### Software authority

Source code stays in vitoohugo333/COMSTUMROM on the authorized work line refactor/customrom-adb-s23-premium.

### Operational control channel

Initial private control bus: viluadmcontas2-dot/AgentRed.

CUSTOMROM jobs use a separate title namespace:

    [CUSTOMROM JOB]

The existing AgentRed host receiver only owns its own AgentRed namespace. A CUSTOMROM Issue is not an AgentRed Windows job.

The control-repository choice is transport configuration and must not leak into the rest of the execution architecture.

## 5. Core architecture

Only the minimum new components are introduced.

### 5.1 GitHubIssueReceiver

Responsibilities:

- query the configured private repository for open Issues with the exact CUSTOMROM title prefix;
- read issue author, number, title and body;
- ignore unrelated Issues;
- validate contract before any ADB execution;
- hand a valid job to the remote-operation layer;
- publish the final receipt;
- close a terminally completed or rejected job when appropriate.

It MUST NOT execute shell directly.

### 5.2 CustomromJobContract

A strict parser for schema customrom.adb.job.v1.

Unknown or malformed material fields fail closed.

A job has exactly one execution mode: typed action or explicit shell.

Example typed action:

    {
      "schema": "customrom.adb.job.v1",
      "requestId": "cr-20260927-0001",
      "target": "taytech-primary",
      "mode": "action",
      "action": "diagnostic.memory",
      "args": {},
      "timeoutSeconds": 60,
      "allowChanges": false
    }

Example advanced shell:

    {
      "schema": "customrom.adb.job.v1",
      "requestId": "cr-20260927-0002",
      "target": "taytech-primary",
      "mode": "shell",
      "command": "dumpsys activity services",
      "timeoutSeconds": 60,
      "allowChanges": false
    }

The Issue-provided risk value, if any, is informational only. Local CUSTOMROM policy is authoritative.

### 5.3 IssueJobStore

Small persistent local store keyed by requestId.

Required states:

    RECEIVED
    CLAIMED
    RUNNING
    COMPLETED
    FAILED
    REJECTED
    UNCERTAIN

The store exists to prevent accidental replay.

Rules:

- same requestId + same canonical contract after terminal completion → return/repost stored receipt; do not execute again;
- same requestId + different contract → reject as replay conflict;
- persist RUNNING before sending an effectful ADB command;
- persist terminal result before publishing the GitHub receipt;
- if the app restarts with an effectful job left RUNNING, move it to UNCERTAIN and do not automatically replay it.

This is intentionally smaller than AgentRed checkpoints while preserving the important safety property.

### 5.4 RemoteOperationRegistry

Typed human operations map to known ADB behavior.

Initial families should reuse existing recipes/engines where possible:

- diagnostic.memory
- diagnostic.cpu
- diagnostic.system
- package.inspect
- package.forceStop
- package.disable
- package.enable
- settings.animations
- adb.persistence.inspect
- recipe.run

The registry is not required to cover every investigation before launch because explicit shell remains available for advanced discovery.

Useful shell discoveries can later be promoted into typed operations.

### 5.5 Existing AdbRemoteController

The current AdbRemoteController remains the only remote shell transport authority.

The new feature consumes its existing connection reuse, mDNS/known-endpoint reconnection, command timeout, stdout, stderr, exit code, duration, transport error and cancellation/reset behavior.

No second ADB implementation is permitted.

### 5.6 Existing PremiumSafetyPolicy

Every remote command/action is classified locally.

The Issue cannot downgrade risk.

**GREEN**
Read-only/diagnostic. May execute automatically when issuer, repository, target and contract are valid.

**YELLOW**
Reversible state change. Allowed only when allowChanges=true. Typed actions MUST capture prior state/rollback when applicable. Local ChangeLedger records the change.

**RED**
Structural/destructive/vehicle-risk boundary. Remote automatic execution is blocked. The Issue receives a rejected receipt explaining the boundary.

Current RED boundary includes root/remount/bootloader/flash/AVB/MCU/CAN firmware and equivalent structural operations.

Automotive components remain protected by presumption.

## 6. Authentication and secret handling

No GitHub token may be committed to the repository, baked into source constants, included in logs or returned in receipts.

The app receives a fine-grained GitHub credential through local setup.

Minimum intended permission is one private control repository, Issues read/write, and repository metadata as required by GitHub.

The credential must be stored using Android platform secure storage/Keystore-backed handling.

The receiver validates at minimum exact repository, exact title prefix, exact allowed GitHub author/login, supported schema, valid requestId, and target matching the configured TayTech controller profile.

The initial expected issuer is the owner's authorized GitHub identity used for the private control repository.

## 7. End-to-end execution

Normal path:

    1. ChatGPT creates [CUSTOMROM JOB] Issue
    2. S23 receiver discovers Issue
    3. validate author + contract + target
    4. persist CLAIMED
    5. resolve typed action or shell
    6. classify locally with PremiumSafetyPolicy
    7. ensure/recover ADB connection
    8. persist RUNNING
    9. execute through AdbRemoteController
    10. interpret result
    11. verify effect when the action has a defined verification
    12. append local session / ChangeLedger evidence
    13. persist terminal receipt
    14. comment receipt on Issue
    15. close terminal Issue

Transport failure and command failure are distinct.

A GitHub failure after ADB execution must not cause the ADB command to be rerun. The receiver retries receipt publication from local terminal state.

## 8. Receipt contract

The Issue response is human-first, technical-second.

Example:

    ✅ Diagnóstico de memória concluído

    TayTech respondeu normalmente.
    RAM, swap e pressão de memória foram coletadas.

    Estado: CONCLUÍDO
    Risco: VERDE
    Duração: 3.2 s
    ADB: conectado

    Detalhes técnicos
    requestId: cr-20260927-0001
    exitCode: 0
    transportError: none

For a reversible change, the receipt includes human object name, package when relevant, previous state, current state and rollback availability.

Receipts must never include secrets/tokens, must bound/truncate very large output, clearly mark truncation, distinguish stdout/stderr/transport error, include rollback information when it exists, and avoid claiming success only from request acceptance.

## 9. UX/UI contract

### 9.1 No new GitHub destination

Do not add GitHub to bottom navigation.

The transport should feel like a capability of CUSTOMROM.

### 9.2 Compact global remote state

When healthy:

    TayTech ● conectada        Remoto ● ativo

No large control-plane dashboard.

When there is a material problem, the state grows and explains authentication missing, GitHub unavailable, ADB disconnected, job rejected, effect uncertain or receipt pending.

Normality is compact; failure earns space.

### 9.3 Commands surface

Remote work appears as human work, not as protocol plumbing.

Example:

    Executando remotamente

    Investigar lentidão do Spotify
    ● Coletando memória, CPU e serviços

    Ver detalhes técnicos

Execution phases shown to the user may be:

    Recebido → Preparando → Executando → Verificando → Concluído

Do not invent percentage progress.

### 9.4 Terminal

A shell job may append to the same session/evidence model used by Terminal, with provenance Remoto.

Terminal remains the place for arbitrary exploration, multiline shell, full technical stdout/stderr, interruption/history.

Routine commands discovered through Terminal can later become typed actions.

### 9.5 Apps

Remote package actions update the same visual application state used by local actions.

The UI continues translating:

- am force-stop → Parar;
- pm disable-user → Desativar;
- pm enable → Restaurar;
- dumpsys package → Analisar aplicativo.

Rollback stays visible beside the changed object.

### 9.6 Mandatory states

The remote capability must have explicit UI behavior for remote control not configured, authenticated/idle, polling/checking, job received, ADB connecting, executing, verifying, completed, failed, rejected by safety, uncertain after interruption, and receipt waiting to publish.

No false green state.

### 9.7 Visual language

Preserve the approved PremiumOps direction.

Binding principles from CUSTOMROM/Omega Dev:

- dark technical premium surface;
- hierarchy before decoration;
- cards only for semantic units;
- borders more than heavy shadows;
- accent reserved for primary navigation/action;
- green = healthy/success;
- yellow = attention/reversible change;
- red = failure/danger;
- human summary first;
- technical disclosure second;
- large touch targets;
- S23 portrait/one-hand behavior;
- keyboard must not destroy Terminal usability;
- navigation must preserve context.

Do not migrate UI framework merely for aesthetics.

## 10. Investigating the multimedia/vehicle stack

The bridge is explicitly intended to support engineering discovery.

Read-only investigation may explore, when exposed by the Android target:

- packages and APK metadata;
- processes and parent/child relationships;
- Android services;
- Binder services/interfaces;
- system properties;
- mounted filesystems;
- native libraries;
- JNI-related artifacts;
- Unix/network sockets;
- device nodes and permissions;
- filtered logcat;
- dumpsys services;
- OEM launchers/services;
- Jancar/Car Info components;
- Bluetooth/audio/DSP/radio integration;
- CAN/MCU-facing Android components.

The objective is to progressively map:

    Android UI/app
       ↓
    service / Binder / socket / JNI
       ↓
    OEM daemon / HAL / native library
       ↓
    MCU / CAN-box interface
       ↓
    vehicle

Observation and protocol mapping are allowed under normal GREEN investigation.

Active CAN transmission, MCU writes, firmware writes or equivalent physical-control experiments remain a separate explicitly authorized boundary.

## 11. Failure behavior

**GitHub unavailable before execution:** keep the job unclaimed locally and retry discovery later. No ADB effect occurs.

**ADB unavailable:** return/retain a failed transport result. Do not pretend the shell command failed.

**Command exit code non-zero:** record command failure with stdout/stderr.

**App killed during a read-only GREEN command:** the job may become UNCERTAIN if execution state cannot be proven. Do not fabricate completion.

**App killed during effectful YELLOW command:** mark UNCERTAIN. Never blindly repeat the effect. A future reconciliation may inspect actual state and then decide whether the intended state already exists.

**Receipt publication fails after execution:** keep the terminal receipt locally and retry publishing only the receipt.

## 12. Implementation boundaries

Expected focused additions, subject to minor naming adaptation if the existing architecture suggests a better fit:

    app/src/main/java/com/customrom/adb/
      GitHubIssueReceiver.kt
      CustomromJobContract.kt
      IssueJobStore.kt
      RemoteOperationRegistry.kt
      GitHubIssueClient.kt
      RemoteControlState.kt

Expected integration points:

- PremiumOpsActivity.kt;
- AdbRemoteController.kt as consumer only unless a focused transport fix is proven necessary;
- PremiumSafetyPolicy;
- ChangeLedger;
- existing recipes;
- session/evidence model.

Avoid a second state store when an existing model can cleanly own the data.

## 13. Tests

Minimum software-side evidence:

1. valid action contract parses;
2. valid shell contract parses;
3. unknown schema rejects;
4. malformed requestId rejects;
5. wrong target rejects;
6. unauthorized author rejects;
7. unrelated Issue prefix is ignored;
8. duplicate requestId/same contract does not rerun;
9. duplicate requestId/different contract rejects;
10. GREEN diagnostic executes through the ADB abstraction;
11. YELLOW action rejects without allowChanges;
12. YELLOW typed action records prior state/rollback where applicable;
13. RED remote action is blocked;
14. transport error differs from shell exit failure;
15. receipt output is bounded and secrets are redacted;
16. terminal result is persisted before GitHub publication;
17. interrupted effectful job becomes UNCERTAIN and does not replay;
18. existing PremiumOps/ADB tests stay green;
19. Android debug build succeeds.

Physical validation is a separate gate and must prove on the real S23 → TayTech route: Issue discovered, ADB command reaches TayTech, result returns to the same Issue, duplicate processing does not repeat the effect, one safe reversible action can be changed and restored, UI remains usable while a remote command runs, and app restart does not blindly replay an uncertain change.

## 14. Acceptance

This design is complete when the owner can, from ChatGPT, create a private GitHub Issue such as:

> Inspect memory pressure on the TayTech.

and the existing CUSTOMROM app on the S23 receives it, validates it, connects to the TayTech, runs the intended ADB observation, preserves the result locally, posts the result back to GitHub and shows the operation coherently in PremiumOps.

For reversible package/settings operations, the same path must preserve before/after state and rollback.

The feature is successful when GitHub feels like an invisible remote-control transport, the S23 feels like the trusted bridge, and the TayTech remains observable and controllable through the same ADB engine already proven by CUSTOMROM.

## 15. Architectural verdict

**Build the bridge, not a new platform.**

    GitHub Issue → S23 CUSTOMROM → existing ADB engine → TayTech → receipt

Power comes from the ADB surface and the owner's ability to ask ChatGPT to investigate or act. Product quality comes from CUSTOMROM/Omega Dev UX rules keeping that power understandable, reversible and evidence-backed.
