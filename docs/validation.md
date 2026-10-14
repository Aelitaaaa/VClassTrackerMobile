# Validation — Microsoft SSO and VClass fixes

Source base: `main`, commit `ac63a0ec23e369e3c2b2978922d5b3746989bdc8`.

Working branch: `feature/microsoft-sso-and-vclass-fixes`.

## Public live inspection

On 14 October 2026, the official login page offered separate Google staff and Microsoft student actions. The student action redirected to `login.microsoftonline.com`, with a callback on VClass at `/admin/oauth2callback.php`. No student credentials were entered. The page also displayed a notice that VClass content was being updated.

This inspection confirms the public entry and redirect. It does not validate a student's authenticated course catalog, enrollment forms, MFA, or an Android WebView session handoff.

## Automated checks

Fifteen JVM test cases passed through Gradle with JUnit 4.13.2. The initial thirteen cases also passed with the standalone Kotlin 2.3.20/JUnit runner. They cover:

- authenticated-page detection and rejection of login/callback/anonymous pages;
- VClass-only cookie import, expiration, deduplication, logout, and stale-response rejection;
- alternative course card layouts, reordered query parameters, pagination, names, class metadata, and duplicate IDs;
- keeping My Courses separate from navigation/catalog links;
- recognized self-enrollment forms, enrollment keys, real form values, missing sesskeys, mismatched course IDs, foreign actions, and unsupported consent fields;
- unavailable, manual, guest, and restricted enrollment states;
- WIB/WITA/WIT and ISO dates, invalid event data, stable deadline IDs, HTML/AJAX identity consistency, and time-left expiration.
- Distinguishing empty catalog results from changed/unreadable HTML.

All fixtures are synthetic and contain no private student data. The Keystore implementation, WebView lifecycle, notification permission behavior, and WorkManager need Android runtime testing; these JVM tests do not simulate their success.

All required Gradle checks passed on the final source:

| Check | Result |
| --- | --- |
| `test` | Passed: 15 tests, zero failures/errors |
| `lintDebug` | Passed: zero errors; 28 warnings and 3 hints |
| `lintRelease` | Passed: zero errors; 28 warnings and 3 hints |
| `assembleDebug` | Passed: installable debug APK |
| `assembleRelease` | Passed: unsigned release APK |

Validation used JDK 17, SDK 36, one worker, a clean build, and build-output caching disabled. A clean rebuild replaced a malformed intermediate resource table encountered during the initial resource-link step. No resource linking or validation step was skipped. No project toolchain or dependency versions were downgraded. The final build completed successfully in 2 minutes 30 seconds after dependencies were available.

Remaining lint warnings concern the existing launcher icons/unused launcher vectors, bitmap density locations, an SDK-version guard, and KTX/version-catalog suggestions. None are authentication, TLS, exported-component, or permission errors. Icons were preserved; the app was not redesigned.

A lightweight clock updates remaining deadline time while a screen stays open. Expired items disappear from the dashboard/tasks list, and an open task detail changes to expired. A clock regression test verifies the urgency/time-left transition without changing the deadline's WIB display.


## Runtime checks still required

- Fresh Microsoft login, cancellation, Android Back, return to VClass, and MFA when requested.
- Restart with a valid session; expired-session return to login; a temporary network failure during startup.
- Logout while sync is running; account changes; absence of old tasks and notifications.
- Live searches such as `1IA10`, `Algoritma`, course code, partial title, and semester when matching data exists.
- Already enrolled, self-enrollment, enrollment key, approval/manual, disabled/restricted, and guest access on courses that expose those states.
- Verify enrollment in My Courses, automatic refresh on return, deadlines, task URLs, and notification navigation.
- Notification permission denial/re-enabling and scheduled WorkManager execution on a physical device.

## Publication gates

The feature branch is published through the GitHub web interface because the connected integration lacked write permission. The current PR and GitHub Actions results are available on GitHub. Local build results above do not establish CI success or live authentication/enrollment success.

Do not merge until the required build/CI and live authentication/enrollment validation are complete. Do not create a release until physical-device Microsoft login is confirmed separately.
