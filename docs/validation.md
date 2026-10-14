# Validation — Microsoft SSO and VClass fixes

Source base: `main`, commit `ac63a0ec23e369e3c2b2978922d5b3746989bdc8`.

Working branch: `feature/microsoft-sso-and-vclass-fixes`.

## Public live inspection

On 14 October 2026, the official login page offered separate Google staff and Microsoft student actions. The student action redirected to `login.microsoftonline.com`, with a callback on VClass at `/admin/oauth2callback.php`. No student credentials were entered. The page also displayed a notice that VClass content was being updated.

This inspection confirms the public entry and redirect. It does not validate a student's authenticated course catalog, enrollment forms, MFA, or an Android WebView session handoff.

## Authenticated browser inspection

On 14 October 2026, the user completed Microsoft sign-in themselves in Cloud Browser and returned to authenticated VClass `/my/`. The page had a logout link and two distinct enrolled-course cards. The same two courses appeared in Kursusku. The `Algoritma` query had an explicit empty result, while `Praktikum` returned 16 distinct catalog course IDs through `/course/search.php?search=Praktikum`. Course titles and card selectors matched the existing parser. No native search pagination was needed for those results.

An unenrolled course redirected to `/enrol/index.php` and offered self-enrollment without a key through an official single-button GET form containing `id`, `action`, and `sesskey`. The application routes this unsupported native form through the official enrollment page, then checks My Courses after returning. The enrollment button was not submitted during inspection; the account membership was not changed.

The current theme omits the old `.usertext` display-name elements and does not include a user ID in `M.cfg`. Its own-account footer profile link provides the account ID and display name. Session verification now uses that footer fallback, and enrollment-browser cookie handoff compares account IDs instead of display names. Missing or conflicting identity is rejected. Two synthetic regression tests cover identical display names, missing identity, other users, foreign links, and conflicting profile IDs.

Browser sign-in and HTML inspection do not verify Android WebView cookie handoff, app restart, logout, or a completed enrollment. Those remain device/runtime checks. No account cookies or private student data were put in source or fixtures.

## Android user confirmation

On 14 October 2026, the user reported that the updated debug APK worked after being asked to test Microsoft sign-in, My Courses, and reopening the app. This is recorded as user-reported Android login success, rather than an independently observed device test. Completed enrollment and membership refresh, logout, deadline/task behavior, notifications, and scheduled background sync have not been individually confirmed on the device.

The APK tested was built from `f248cb50ebc48093797bf4489148e6f11415122a` in successful PR CI run `38570025455`. Its SHA-256 is `bcb3d5db1632a9e201cafce97de534ef99d3595a35e13417056049238fd8ae94`. That run passed 17 JVM tests, both lint checks with zero errors, and both debug and unsigned release builds. This follow-up changes documentation only. The PR remains open while the remaining relevant runtime checks are pending. No release is created automatically.

## Baseline automated checks

The baseline fifteen JVM test cases passed through Gradle with JUnit 4.13.2. The initial thirteen cases also passed with the standalone Kotlin 2.3.20/JUnit runner. They cover:

- authenticated-page detection and rejection of login/callback/anonymous pages;
- VClass-only cookie import, expiration, deduplication, logout, and stale-response rejection;
- alternative course card layouts, reordered query parameters, pagination, names, class metadata, and duplicate IDs;
- keeping My Courses separate from navigation/catalog links;
- recognized self-enrollment forms, enrollment keys, real form values, missing sesskeys, mismatched course IDs, foreign actions, and unsupported consent fields;
- unavailable, manual, guest, and restricted enrollment states;
- WIB/WITA/WIT and ISO dates, invalid event data, stable deadline IDs, HTML/AJAX identity consistency, and time-left expiration.
- Distinguishing empty catalog results from changed/unreadable HTML.

All fixtures are synthetic and contain no private student data. The Keystore implementation, WebView lifecycle, notification permission behavior, and WorkManager need Android runtime testing; these JVM tests do not simulate their success.

Before the authenticated-browser follow-up, all required Gradle checks passed:

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

- Cancellation, Android Back, and MFA when requested; successful Android Microsoft login is user-reported above.
- Restart with a valid session; expired-session return to login; a temporary network failure during startup.
- Logout while sync is running; account changes; absence of old tasks and notifications.
- Live searches such as `1IA10`, `Algoritma`, course code, partial title, and semester when matching data exists.
- Already enrolled, self-enrollment, enrollment key, approval/manual, disabled/restricted, and guest access on courses that expose those states.
- Verify enrollment in My Courses, automatic refresh on return, deadlines, task URLs, and notification navigation.
- Notification permission denial/re-enabling and scheduled WorkManager execution on a physical device.

## Publication gates

The feature branch is published through the GitHub web interface because the connected integration lacked write permission. The current PR and GitHub Actions results are available on GitHub. Local build results above do not establish CI success or live authentication/enrollment success.

Do not merge until the required build/CI and live authentication/enrollment validation are complete. Do not create a release until physical-device Microsoft login is confirmed separately.
