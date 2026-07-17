# Phase 0 Build Blocker — GitHub Actions Not Executing

_Last checked: 2026-07-17_

## Expected behavior

`.github/workflows/ci.yml` should run for:

- pushes to `refactor/product-reset`;
- pull requests targeting `master`.

The workflow runs:

```bash
./gradlew --version
./gradlew testDebugUnitTest lintDebug --stacktrace
```

## Observed behavior

- no workflow run is associated with the branch commits inspected through the connected GitHub integration;
- no workflow run is associated with the pull-request merge commit;
- no combined commit status/check is present;
- therefore there is no evidence yet that the current branch builds or fails.

## Interpretation

This is an **execution/activation blocker**, not a successful build and not a known build failure.

Likely causes to verify in GitHub UI/settings include:

- GitHub Actions disabled for the repository/account;
- workflow execution requiring initial manual enablement;
- Actions permissions or policy preventing the workflow;
- the new workflow not being recognized until enabled from the Actions tab;
- a repository-level restriction not exposed by the connected integration.

## Required resolution

1. Open the repository’s **Actions** tab.
2. Enable workflows if GitHub presents an enablement prompt.
3. Open the **Android CI** workflow.
4. Re-run or trigger it by updating the branch after enablement.
5. Confirm `testDebugUnitTest` and `lintDebug` pass or capture logs for repair.

## Exit condition

Phase 0 build verification is complete only when a specific commit has a successful CI run or a reproducible clean local build/lint transcript is recorded.
