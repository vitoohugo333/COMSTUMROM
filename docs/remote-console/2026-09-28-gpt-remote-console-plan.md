# GPT Remote Console over CUSTOMROM — execution plan

## Goal

Evolve the existing GitHub Issues remote bridge into a persistent GPT-operated console over the TayTech, with the Galaxy S23 as the ADB bridge.

## Transport model

The implementation keeps one durable GitHub Issue per command/mission. Related commands share a logical `sessionId` and monotonically increasing `sequence`.

```text
GPT
  ↓ create [CUSTOMROM JOB] with sessionId + sequence
GitHub Issues
  ↓ poll every ~5 s with conditional ETag requests
CUSTOMROM / S23
  ↓ existing admission + safety + anti-replay
AdbRemoteController
  ↓
TayTech
  ↓
terminal receipt persisted locally then published to the Issue
  ↓
GPT reads receipt and emits the next sequence in the same session
```

This intentionally avoids a new backend, WebSocket server, embedded LLM, or a second ADB stack.

## Invariants

- Existing job Issues without `sessionId` remain compatible.
- `requestId` stays the idempotency identity.
- `sessionId + sequence` adds causal ordering, not replacement identity.
- A later sequence cannot run until the prior sequence receipt is successfully published to GitHub.
- Receipt publication failure cannot cause ADB replay.
- GREEN/YELLOW/RED remains local and authoritative.
- UNCERTAIN never blindly replays.
- Token remains Android-Keystore protected.
- No new primary navigation destination is created.
- Polling uses ETag conditional GET and a 5-second default only while remote control is enabled/running in the app lifecycle.

## Software acceptance

1. Job contract accepts optional valid session metadata and rejects half-formed session metadata.
2. Sequence 2 waits until sequence 1 receipt is visible on GitHub.
3. Session progress survives process restart.
4. Legacy jobs remain executable.
5. GitHub GET sends `If-None-Match` after the first ETag and reuses cached body on HTTP 304.
6. Receipts expose session and sequence when present.
7. JVM tests, native validator and Android debug build pass on `main`.

## Physical gate

Software evidence cannot prove S23/TayTech runtime behavior. Final physical proof remains: real token, real polling, real ADB, receipt round-trip, then one reversible YELLOW operation + rollback.
