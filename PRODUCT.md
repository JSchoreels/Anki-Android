# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

- **The maintainer** (primary): studies only on an Android phone and tablet, daily, in short sessions (bus, couch, between things). German first, plus English and Japanese decks in one collection synced through AnkiWeb. Uses the Anki XP day theme.
- **Public release users**: anyone installing kuma3 from the GitHub releases (directly or through Obtainium). They get the same app; Anki XP is an opt-in day theme, so the stock look must keep working for them.

## Product Purpose

kuma3 is a personal AnkiDroid build (2.26.0alpha2) on the JSchoreels FSRS-7 fork of Anki, with the fork's RWKV recall model running on the phone (RWKV-Instant review queue, RWKV forecast, recall chart) and a few deck list extras.

The **Anki XP** day theme exists to make daily reviews fun enough that the reps actually get done: the whole app dressed as Windows XP, with the Anki XP game (XP per answer, combos, multipliers, boss cards, daily quests, levels, trophies, 8-bit sounds) running inside normal reviews. Success = the streak holds and the daily goal gets met, without the game ever corrupting scheduling or the collection.

## Positioning

The only AnkiDroid with on-device RWKV-Instant + FSRS-7 scheduling, and the only one where real reviews of your real collection are a game whose progress is shared with kuma3 Skins (same "🐻 Anki XP progress" notes, one total across apps and devices).

## Operating Context

- Native Kotlin screens (deck list, reviewer, browser, dialogs) themed through `DayTheme.XP` and `com.ichi2.anki.xp`; backend web pages (deck options, statistics, card info, congrats, import, image occlusion) restyled by `assets/xp/pages.css`, injected by `PageWebViewClient`.
- Phone first (Pixel-class, 1080 px wide, DPR ~2.6), tablet second. Touch only.
- Reviews are one-handed and quick; settings pages (deck options) are visited rarely but decide where limits and schedulers are saved.
- Releases: GitHub Actions builds `kuma3-dev` on every push to `rwkv`; numbered releases with English + Spanish notes in `kuma3/release-notes/`.

## Capabilities and Constraints

- Anki XP is one of four day themes (Light, Plain, E-Ink, Anki XP); switching takes effect immediately and Off must mean stock kuma3.
- Retro CRT scanlines can be turned off in the XP settings.
- Backend pages are styled only through Anki CSS variables and a few generic elements, so a backend update degrades the look instead of breaking a page.
- The game must never touch scheduling: XP is written to the progress note only on reviewer exit (a raw write clears Anki undo, so never mid-review); undoing an answer takes back its XP.
- Upstream changes stay minimal (one-line hooks in existing screens); the AnkiDroid AI_POLICY applies to anything sent upstream.

## Brand Commitments

- Name: **kuma3** (lowercase). The XP theme is called **Anki XP**.
- The Windows XP look is binding for the Anki XP theme: Luna title bars, Tahoma (Wine copy), XP push buttons, message boxes, group boxes, the XP selection blue.
- Release notes are plain and specific, English then Spanish. A personal build, shared as is; never imply endorsement by AnkiDroid, Anki or the FSRS-7 fork.

## Evidence on Hand

- Release notes `kuma3/release-notes/kuma3-<n>.md` (release 15 introduced Anki XP).
- Source of the game rules and look: the kuma3 Skins "Anki XP" skin.
- No user counts, testimonials or metrics exist; do not invent them.

## Product Principles

1. Reps first: every screen should get the user into reviews or back out of them faster; the game rewards studying, never replaces it.
2. Fun never costs data: no game feature may risk the collection, scheduling, undo or sync.
3. Committed to the bit: in Anki XP every surface, native or web, should look like XP, not "Anki tinted blue".
4. Opt-in and reversible: stock kuma3 stays first-class for public users; XP on/off is instant and clean.
5. Phone first: decisions are made at phone width, one-handed.

## Accessibility & Inclusion

- WCAG AA contrast for text and controls on light themes (release 15 set 4.5:1 for counts and forecasts); with Anki XP, measure against the scanline rows, not plain white.
- Touch targets at least 24 px, and 32-44 px for controls that change settings.
- Controls keep their accessible names and states (aria-pressed tabs, labelled radios, named buttons); XP styling must not hide state.
