# G002 product-quality acceptance ledger

Design authority: `G002_Product_Quality_Spec.md`  
Progress authority: `G002_V1_ActiveMilestone.md`  
Baseline: app `1.0.2`, versionCode `4`; authoritative commits are recorded in Git history.

Status vocabulary: `UNREVIEWED / IMPLEMENTED_UNVERIFIED / AUTOMATED_PASS / EMULATOR_PASS / DEVICE_PASS / HUMAN_PASS / BLOCKED / N/A`.

## Initial evidence map — 2026-09-29

| IDs | Status | Confirmed evidence | Remaining acceptance work |
|---|---|---|---|
| Q01, Q02, Q53, Q59 | AUTOMATED_PASS | Correct G002 root and HEAD verified; only protected untracked `app/release/`; no physical-device or production-key operation | Continue preserving these boundaries |
| Q04–Q13, Q15, Q25, Q28, Q36 | AUTOMATED_PASS | Rules/session/UI-selection tests cover initial setup, movement, promotion, drops, nifu, drop-mate, check evasions, checkmate, repetition, legal candidates, factual coaching, and post-game stop | Q06/Q07/Q08 still require current-build screen-path evidence |
| Q16, Q19, Q31, Q38 | IMPLEMENTED_UNVERIFIED | Selection isolation, stale result rejection, undo, owner rotation and board labels have pure tests | Add request-id/game-id coverage and UI double-tap checks |
| Q17, Q18, Q20–Q24, Q27, Q29, Q30, Q34, Q37 | IMPLEMENTED_UNVERIFIED | Compose board/coaching/match flows and strategy tests exist; older emulator/device evidence is recorded | Current-build emulator images and Human Gate remain |
| Q32, Q33, Q35 | UNREVIEWED | A small tactical/opening suite and ten-ply tests exist | Fixed 60+30 benchmark, three real difficulties, and natural full-game records are missing |
| Q39–Q45 | UNREVIEWED | In-memory move history only | Clock, persistent save/resume, record management and import/export are missing |
| Q46–Q48 | UNREVIEWED | Square semantics exist | Sound/haptics, complete TalkBack path and help content are missing |
| Q49, Q50, Q57, Q58 | BLOCKED | Earlier limited timing and device records exist | Current candidate performance, endurance, physical-device pass and owner Human Gate require a later authorized device window |
| Q51, Q52, Q54–Q56, Q60 | IMPLEMENTED_UNVERIFIED | Privacy/listing/release docs and unsigned builds exist | Dependency/license audit, current signed candidate and final store consistency remain |

## First implementation slice

1. Add an explicit game setup model with Sente/Gote and three real search budgets.
2. Add versioned local active-game serialization and resume without deleting prior data.
3. Connect the home/setup/game flow to save every confirmed move and restore from the last confirmed position.

The ledger is evidence tracking, not a second design specification. Each later update must name the relevant tests, build, commit, and environment.

## WP-A / first WP-E slice — 2026-09-29

| IDs | Status | Evidence | Remaining work |
|---|---|---|---|
| Q03 | IMPLEMENTED_UNVERIFIED | Home now presents Continue, coaching, and AI match; setup is a separate screen | Current-build UI and process recreation not yet exercised |
| Q33 | IMPLEMENTED_UNVERIFIED | `GameDifficulty` has three strictly increasing depth/node/time budgets; teacher stays on the strong budget | The required fixed-seed 20-pair strength comparison is not yet run |
| Q34 | IMPLEMENTED_UNVERIFIED | Sente/Gote and difficulty are selected before either mode; Gote starts with exactly one AI turn through the existing state machine | Random side and full-game screen evidence remain |
| Q40, Q41 | IMPLEMENTED_UNVERIFIED | Versioned active-game codec saves each confirmed move; restore replays every move through `ShogiRules`; malformed or illegal data is preserved under a quarantine key | Review provenance, clock and completed-record archive are not yet persisted; process/device integration remains |
| Q42 | IMPLEMENTED_UNVERIFIED | Persistence uses a new private key and does not overwrite any pre-existing v1.0.1 storage | Explicit update-install migration evidence remains |
| Q54, Q60 | AUTOMATED_PASS | 94/94 unit tests, Debug APK and unsigned Release AAB built from this worktree; hashes recorded in ActiveMilestone | Signing and current visual evidence remain |

Tests: `GameConfigurationTest`, `SavedGameTest`, and restored-session cases in `ShogiGameSessionTest`.  
Build environment: PC/JVM, no physical Android device. Test result: 94 tests, 0 failures, 0 errors, 0 skipped, 11 suites.

## WP-B stale-result hardening — 2026-09-29

| IDs | Status | Evidence | Remaining work |
|---|---|---|---|
| Q31 | AUTOMATED_PASS | Position analysis, AI move and learner-review requests receive a monotonically new request ID. Starting a newer request rejects the older result even when game position and revision are otherwise identical. Restart, undo and committed moves invalidate pending IDs. | Emulator cancellation latency remains part of Q49 |
| Q25, Q26 | AUTOMATED_PASS | `AnalysisRequest` and `AnalysisResult` carry the request identity without changing candidate/PV legality or score perspective | Mate score remains integer-based and must be revisited with the larger benchmark |
| Q54 | AUTOMATED_PASS | 96/96 tests; Debug APK and unsigned Release AAB regenerated | Signed artifact remains owner-gated |

Tests: `olderRequestForTheSamePositionIsRejectedAfterANewerRequestStarts`, `olderReviewForTheSameMoveIsRejectedAfterANewerReviewStarts`, and all prior suites.
Result: 96 tests, 0 failures, 0 errors, 0 skipped, 11 suites. No physical device was used.

## Security baseline hardening — 2026-09-30

| IDs | Status | Evidence | Remaining work |
|---|---|---|---|
| Q51 | IMPLEMENTED_UNVERIFIED | Release merged manifest has no `INTERNET` permission, explicitly rejects cleartext traffic, and limits Android backup to `g002_local_games_v1.xml`; the privacy policy now discloses OS backup/device transfer | Final Play Data Safety answers and installed-build inspection remain |
| Q53 | AUTOMATED_PASS | The owner used the existing local G002 upload key in Android Studio without sharing its passwords; the signed vc4 certificate SHA-256 matches the prior vc3 upload certificate exactly | Owner continues to control the key and Play submission |
| Q54, Q60 | AUTOMATED_PASS | 96/96 tests; versionCode `4` / versionName `1.0.2`; unsigned AAB SHA-256 `9C5E62749C406E6F264D4E65458BA34155F45F92D96774E2112457CCD6679CEE`; signed AAB SHA-256 `43B9707752780113F404F7D09941D618E2CD564CBAF309617875347BF7107475`; `jarsigner` verification succeeded | Play submission and current visual evidence remain |

Validation: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:bundleRelease`, merged-manifest inspection, `jarsigner` verification, upload-certificate comparison, signed/unsigned bundle-entry comparison, and `git diff --check`. The only non-signature entry difference was generated VCS metadata; executable, resource and manifest payloads matched. No emulator or physical device was used.

## Coaching mate-wording hardening — 2026-10-08

| IDs | Status | Evidence | Remaining work |
|---|---|---|---|
| Q28 | AUTOMATED_PASS (forced-mate wording scope) | User-move reviews now distinguish a confirmed mating line, a missed candidate mating line, and an opposing mating line from ordinary numeric guidance. The raw mate sentinel is not rendered as a centipawn-like score. Three regression cases cover those paths. | Broader on-screen and Human explanation-quality review remains required. |
| Q54, Q60 | AUTOMATED_PASS (current source/build scope) | 127/127 unit tests, Debug APK and unsigned Release AAB built after the change; `git diff --check` passed. | Signed artifact and current device evidence remain separate gates. |

No physical device, GPL executable, evaluation data, signing key, or Play Console action was used. This wording hardening does not establish a human rank or a five-dan-equivalent engine.

## Search-limit confidence wording — 2026-10-08

| IDs | Status | Evidence | Remaining work |
|---|---|---|---|
| Q26, Q28 | AUTOMATED_PASS (local search-limit scope) | The local engine records when its own node/time/cancel budget stops the search. Candidate and review wording then identify the score as guidance; two regression cases cover a limited candidate result and learner-review text. | Explanation quality on device and external-backend timing remain separate gates. |
| Q54, Q60 | AUTOMATED_PASS (current source/build scope) | 129/129 unit tests, Debug APK and unsigned Release AAB built after the change; `git diff --check` passed. | Signed artifact and current device evidence remain separate gates. |

No physical device, GPL executable, evaluation data, signing key, or Play Console action was used. This confidence wording does not establish a human rank or a five-dan-equivalent engine.

## Strong-budget depth-five regression — 2026-10-08

| IDs | Status | Evidence | Remaining work |
|---|---|---|---|
| Q27, Q33 | AUTOMATED_PASS (current strong-budget scope) | `STRONG` and teacher analysis now allow depth 5 while retaining the existing 160,000-node / 2,000-ms bounds. The fixed 30-position strategy set runs through that actual product request and retains 28/30 (93.3%) top-three overlap with the isolated reference. JVM suite time: 44.98 s. | Current-device latency, broader strategic benchmark, and independent rank calibration remain required. |
| Q54, Q60 | AUTOMATED_PASS (current source/build scope) | 129/129 unit tests, Debug APK and unsigned Release AAB built after the change; `git diff --check` passed. | Signed artifact and current device evidence remain separate gates. |

No physical device, GPL executable, evaluation data, signing key, or Play Console action was used. This budget increase does not establish a human rank or a five-dan-equivalent engine.
