# Physical test label

`needs-physical-test` marks a PR that touches audio capture, the microphone or transcription. CI cannot inject reliable audio (neither on the emulator nor on the Pixel), so such a PR needs a manual test on a physical device with Pierre before merge. PRs without the label are validated by CI alone.

The keyboard (IME) stays testable on the emulator and is not concerned, unless the change touches recording or transcription.

## How the label gets on a PR

- **Automatically**: `.github/workflows/labeler.yml` runs `actions/labeler` on every PR targeting `develop` or `main` (opened, new commits, reopened). It adds the label when a changed file matches one of the globs in `.github/labeler.yml`, the single list of audio/mic/transcription zones. Edit that file to change the scope.
- **By hand**: when a change outside those zones still impacts audio indirectly, add the label yourself:

  ```bash
  gh pr edit <number> --add-label needs-physical-test
  ```

The workflow never removes the label (`sync-labels: false`), so a manual label survives new pushes, and so does an automatic one after the matching files are reverted.

## Who removes it, and when

Only **Titus** removes the label, through the GitHub API, as soon as Pierre validates the physical test, orally or in writing. No workflow, no slash command.

```bash
gh pr edit <number> --remove-label needs-physical-test
```

Do not remove it on any other signal (green CI, review approval, an agent's own test run). Do not merge a PR that still carries it.

## Which PRs are waiting for the Pixel

```bash
gh pr list --label needs-physical-test --state open
```

Web view: <https://github.com/getdictus/dictus-android/pulls?q=is%3Apr+is%3Aopen+label%3Aneeds-physical-test>
