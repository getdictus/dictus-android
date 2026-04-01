# Phase 10: Beta Distribution + License Audit - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Every push to main is validated by CI, every version tag produces a signed APK on GitHub Releases, and the project's OSS dependency licenses are audited and correct. README provides beta installation instructions.

</domain>

<decisions>
## Implementation Decisions

### CI workflow design
- Workflow runs: `./gradlew lint`, `./gradlew test`, `./gradlew assembleDebug`
- Triggers: PRs and pushes to main branch
- Runner: `ubuntu-latest` (free for public repos, standard Android CI)
- No emulator/instrumented tests — unit tests only

### Release signing
- APK signing via GitHub Secrets: base64-encoded keystore stored as secret, CI decodes and signs on `v*` tag push
- Version from git tag: tag `v1.1.0` drives versionName, versionCode auto-derived from tag
- Release APK automatically uploaded to GitHub Releases within one CI run

### License audit scope
- `gradle-license-plugin` auto-generates the license list (replaces current hand-coded 4-entry LicencesScreen)
- LicencesScreen reads from plugin-generated report — always complete and up to date
- GPL policy: block GPL dependencies, allow LGPL (MIT project can't include GPL code)
- Build fails if GPL-contaminated dependency detected
- Manual overrides for deps the plugin can't detect (whisper.cpp NDK source, Parakeet models CC-BY-4.0)

### README beta section
- Concise text-only installation instructions (no screenshots)
- Download link + brief steps: enable unknown sources, download APK, install, enable keyboard
- Feedback mechanism: link to GitHub Issues (templates will be created in Phase 11)

### Claude's Discretion
- Exact GitHub Actions workflow YAML structure and job naming
- Gradle task configuration for license plugin
- How to derive versionCode from tag (timestamp, sequential, or tag-based)
- Whether to use APK or AAB for GitHub Releases (APK recommended for sideloading)
- License report format (JSON vs HTML) for LicencesScreen consumption

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing license screen
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/LicencesScreen.kt` — Current hand-coded 4-entry license screen to be replaced by auto-generated approach

### Build configuration
- `app/build.gradle.kts` — App module build config, no signing config yet, packaging block with pickFirsts for libc++_shared.so
- `build.gradle.kts` — Root build config
- `ime/build.gradle.kts` — IME module build config
- `core/build.gradle.kts` — Core module build config
- `asr/build.gradle.kts` — ASR module (sherpa-onnx) build config
- `whisper/build.gradle.kts` — Whisper module build config

### Third-party licenses
- `third_party/whisper.cpp/LICENSE` — MIT license for whisper.cpp (bundled as NDK source)

### Requirements
- `.planning/REQUIREMENTS.md` — BETA-01, BETA-02, BETA-03, LIC-01, LIC-02 requirements

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LicencesScreen.kt`: Existing Compose screen with `LicenceBlock` composable — UI structure can be reused, just needs to read from auto-generated data instead of hardcoded entries
- `LicenceBlock` composable: Shows name, author, URL, license text with monospace formatting — good template for auto-generated entries

### Established Patterns
- Multi-module Gradle project: `app`, `ime`, `core`, `whisper`, `asr` — license plugin must scan all modules
- `libs.plugins.*` version catalog for plugin declarations — new plugins should use same pattern
- `packaging.jniLibs.pickFirsts` already handles native lib conflicts — signing config follows same `android {}` block pattern

### Integration Points
- `app/build.gradle.kts` — Add signing config, license plugin
- `.github/workflows/` — New directory for CI workflows (doesn't exist yet)
- `README.md` — Currently doesn't exist at project root; needs to be created with beta section
- Settings navigation — LicencesScreen already accessible from Settings > About > Licences

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches for Android CI/CD with GitHub Actions.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 10-beta-distribution-license-audit*
*Context gathered: 2026-04-01*
