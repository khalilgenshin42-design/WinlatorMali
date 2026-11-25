# Epic Storefront — Full Experience Redesign

Status: active implementation; CI-validated build published 2026-11-25
Branch: `development/maliwinlator-1.2.1-bootstrap`

## Why the current screen misses the approved concept

The current `EpicFragment` has progressed beyond the original settings-form layout: the Library has a personalized wide-art hero, catalog-backed collection metrics, portrait-art shelves, filter counts, and a separate profile with persistent install controls and real worker progress. Account/setup is separated from the storefront, and provider status is kept out of the hero. The remaining weakness is that the home page is still assembled as a mostly linear vertical stack of generic shelves and panels. The current hero entrance is a one-shot cinematic reveal, and search/category browsing now hides the personalized hero/stat block so filtered results become the primary content. The overall navigation and shelf interactions still need a more cohesive launcher interaction model. Further artwork resizing or palette changes alone will not close that gap.

Increasing artwork sizes or changing colors cannot close that gap. The next implementation must change the page composition and interaction hierarchy, not add another cosmetic pass.

## Target experience

### 1. Storefront home
- Treat the Epic area as a dedicated launcher surface, not a settings form.
- Use a compact brand header with account/avatar state and a clear library identity.
- Feature a selected game in a wide cinematic hero with artwork, gradient scrim, title, real installed/download state, and one context-aware primary action.
- The hero must not arbitrarily select the first API result. Use a deterministic featured-selection policy based only on the user's actual library (for example, retain the last opened title in saved UI state; otherwise choose a stable first eligible title).
- Follow the hero with a prominent horizontally browsable "Your games" cover-art shelf containing the complete non-DLC Epic catalog, including installed and installable titles; smaller secondary shelves (Continue Playing, Installed, Ready to install, Active download) act as shortcuts and may intentionally repeat titles.
- Search and filters should be compact, accessible controls that do not dominate the screen.
- Empty, loading, signed-out, and bridge-unavailable states must have intentional layouts rather than generic status text at the top of the page.

### 2. Immersive game profile
- Open a dedicated profile view with a large artwork backdrop, readable gradient treatment, clear back navigation, title, and context-aware Install / Play / Cancel action.
- Keep real download progress attached to the selected title and visible without navigating to a separate Downloads page.
- Use distinct visual sections for achievements, playtime, and cloud-save status. Render only returned data. Explicitly label unavailable playtime and metadata-only cloud-save support; do not imply sync exists.
- The detail page must preserve scroll position/state appropriately when returning to the storefront.

### 3. Interaction and motion
- Use coordinated transitions between storefront and profile, with reduced-motion/accessibility-safe behavior.
- Add meaningful press/focus feedback and subtle shelf/card motion; avoid replaying all entrance animations on every search or progress update.
- Keep animations short, GPU-friendly, and free of continuous rendering loops.
- Ensure touch targets, contrast, TalkBack labels, and small-screen layouts remain usable.

### 4. Visual system
- Establish a consistent dark premium palette with one restrained accent, typography hierarchy, spacing scale, rounded surfaces, and layered artwork/scrims.
- Use real Epic artwork URLs only after the existing host validation; retain a deliberate fallback for missing art.
- Do not use fabricated game screenshots, descriptions, ratings, playtime, achievement counts, or progress.

## Engineering constraints

- Preserve the current Epic Bridge / Legendary 0.21.1 command contract and foreground download/install service.
- Preserve persisted active-job identity and reconnect behavior across Fragment replacement.
- Keep network/provider work off the main thread and avoid blocking UI navigation while a job runs.
- Do not introduce a second download engine or move large downloads into the Fragment.
- Keep the Epic account/setup workflow available, but separate it from the primary storefront presentation.
- No cloud-save upload/download/sync until title-specific paths, conflict handling, backups, and safe synchronization are implemented.
- Achievements remain read-only Epic metadata; games unlock their own achievements.
- Playtime remains unavailable until a legitimate data source is implemented.

## Implementation sequence

1. Replace the current stacked library composition with a purpose-built storefront screen and state model. Keep the existing command/worker methods intact.
2. Implement the immersive game profile as a distinct screen state with coordinated transitions and preserved active-job UI.
3. Refine artwork loading, shelves, filters, loading/empty/error states, accessibility, and responsive sizing.
4. Run both standard Android and Epic runtime/ARM64 CI workflows. Publish a GitHub prerelease only after both pass.
5. Request Xiaomi 15T visual and active-download/install navigation validation only after the build is published.

## Validation boundary

CI and managed-emulator success do not prove visual fidelity on the Xiaomi 15T. Do not claim the original lost demo has been reproduced pixel-for-pixel without a reference screenshot and device review. If the user can provide a screenshot of the approved demo, use it as the visual reference; implementation must still proceed with the requirements above if no screenshot is available.

## Latest implementation — interactive featured spotlight

Source commit: `07b0bf6498bbe828a26685a8f11709d339e80ed5`

The cinematic hero now has accessible previous/next controls when the catalog contains multiple eligible non-DLC titles. Selection is deterministic and based only on actual catalog entries; the chosen title is persisted as the featured app. Changing the spotlight uses a short, one-shot crossfade/vertical settle and does not start a timer or continuously animate the view. The existing hero action still reflects the selected game's real installed or active-job state. This is an interaction/composition improvement, not a claim that the lost original demo has been reproduced.

## Structural storefront change — primary catalog grid

Source commit: `7a1e791fdc4316e035ee71c866ddfeb55fb6001a`

The primary `YOUR EPIC COLLECTION` surface is now a responsive two-column portrait-art grid instead of a horizontal carousel. Each real non-DLC catalog title occupies a full-width grid cell with its existing status, artwork fallback, click-to-profile behavior, and accessibility description. Continue Playing, Installed, Active Downloads, and Ready to Install remain secondary horizontal shortcut rails. The grid uses half-width cells and 3:4 artwork sizing so both columns fit phone layouts. This intentionally changes the page composition rather than just changing card styling.


## Visual direction — premium launcher pass

Source commit: `a61698506c412dc18de81e1d8576a9688dcfea94`

The storefront header now leads with MaliWinlator identity and a compact Epic-library descriptor rather than presenting Epic Games as the app brand and MaliWinlator as a small badge. The Library hierarchy now reads as a dedicated destination: clear "Your library" title, concise supporting line, cinematic selected-game spotlight and live collection metrics, then search/filter and the full two-column catalog. Catalog cards have tighter, more consistent framing and spacing. This is a coherent visual-system pass built on the two-column grid; it is not a claim of pixel-exact reproduction of the unavailable original demo. The Epic Bridge, Legendary job engine, persisted progress and account workflow are unchanged.


## Approved interactive concept — implementation target (2026-11-25)
The user explicitly approved the interactive concept rendered in chat and requires it to guide the next GitHub build. Treat its visible composition and interaction hierarchy as the target: compact MALIWINLATOR / EPIC GAMES header with account access; Your Library title; cinematic featured title; search; Installed / Ready to install / Downloading filters; two-column portrait game-card grid; and a cohesive game-detail surface with context-aware Install / Cancel / Play / Update / Uninstall and real in-page worker progress.

### First implementation pass
Commit `faa678cdf7834fa3aef56f75a83da70c3f75cba2` removes the repeated secondary rails from the default All games view. The home now keeps the featured spotlight and complete two-column catalog as the primary browsing composition. Filtered categories remain backed by actual catalog and install/job state.

The chat prototype's titles and progress were illustrative. Production UI must use the user's real Epic catalog and real worker status only. Do not fabricate games, progress, ETA, achievements, playtime, cloud-save sync, or DRM compatibility. This pass is not yet a 1:1 completion claim; continue the redesign and validate the APK workflows.
