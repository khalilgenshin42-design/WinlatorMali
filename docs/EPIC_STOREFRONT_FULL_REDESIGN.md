# Epic Storefront — Full Experience Redesign

Status: active implementation; CI-validated build published 2026-11-25
Branch: `development/maliwinlator-1.2.1-bootstrap`

## Why the current screen misses the approved concept

The current `EpicFragment` has progressed beyond the original settings-form layout: the Library has a personalized wide-art hero, catalog-backed collection metrics, portrait-art shelves, filter counts, and a separate profile with persistent install controls and real worker progress. Account/setup is separated from the storefront, and provider status is kept out of the hero. The remaining weakness is that the home page is still assembled as a mostly linear vertical stack of generic shelves and panels. The current hero entrance is a one-shot cinematic reveal, but the overall navigation and shelf interactions still need a more cohesive launcher interaction model. Further artwork resizing or palette changes alone will not close that gap.

Increasing artwork sizes or changing colors cannot close that gap. The next implementation must change the page composition and interaction hierarchy, not add another cosmetic pass.

## Target experience

### 1. Storefront home
- Treat the Epic area as a dedicated launcher surface, not a settings form.
- Use a compact brand header with account/avatar state and a clear library identity.
- Feature a selected game in a wide cinematic hero with artwork, gradient scrim, title, real installed/download state, and one context-aware primary action.
- The hero must not arbitrarily select the first API result. Use a deterministic featured-selection policy based only on the user's actual library (for example, retain the last opened title in saved UI state; otherwise choose a stable first eligible title).
- Follow the hero with a prominent horizontally browsable "Your games" cover-art shelf and smaller secondary shelves only where they represent real data (Installed, Ready to install, Active download).
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
