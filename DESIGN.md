---
name: kuma3 Anki XP
description: Windows XP Luna as the stage for a review game, on an Android phone.
colors:
  luna-blue: "#0054E3"
  luna-deep: "#003DD7"
  luna-highlight: "#0997FF"
  window-frame: "#0831D9"
  selection-blue: "#316AC5"
  hover-tint: "#C1D2EE"
  taskbar-blue: "#245EDB"
  heading-blue: "#215DC6"
  link-blue: "#1D4FA8"
  groupbox-label: "#0046D5"
  title-shadow: "#0F1089"
  start-green: "#3C9A3C"
  progress-green: "#2ED330"
  xp-face: "#ECE9D8"
  face-light: "#F1EFE2"
  button-shade: "#D6D0C5"
  groupbox-frame: "#D0D0BF"
  groupbox-etch: "#B9B6A1"
  control-border: "#7F9DB9"
  button-border: "#003C74"
  balloon-cream: "#FFFFE1"
  paper-white: "#FFFFFF"
  ink-black: "#000000"
  muted-text: "#595959"
  count-new: "#0000FF"
  count-learn: "#C00000"
  count-due: "#008000"
  pop-gold: "#FFD700"
  combo-cyan: "#009DFF"
  multiplier-coral: "#FE5F55"
  chip-cyan: "#0070C0"
  chip-coral: "#C83228"
  answer-again: "#8B0000"
  answer-good: "#004A00"
  answer-easy: "#0000B0"
typography:
  title:
    fontFamily: "XP Tahoma Bold (Wine Tahoma), Tahoma, Roboto, sans-serif"
    fontWeight: 700
  body:
    fontFamily: "XP Tahoma (Wine Tahoma), Tahoma, Roboto, sans-serif"
    fontSize: "15sp"
    fontWeight: 400
  label:
    fontFamily: "XP Tahoma (Wine Tahoma), Tahoma, Roboto, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
  page-heading:
    fontFamily: "XP Tahoma (Wine Tahoma), Tahoma, Roboto, sans-serif"
    fontWeight: 700
rounded:
  control: "3px"
  chip: "4px"
  window-top: "8px"
  balloon: "8px"
spacing:
  groupbox-side: "8dp"
  groupbox-head: "20dp"
  groupbox-foot: "8dp"
  button-gap: "5dp"
  button-pad: "14dp"
components:
  button-push:
    backgroundColor: "{colors.xp-face}"
    textColor: "{colors.ink-black}"
    rounded: "{rounded.control}"
    padding: "0 14dp"
    height: "38dp"
  button-push-pressed:
    backgroundColor: "{colors.button-shade}"
    textColor: "{colors.ink-black}"
    rounded: "{rounded.control}"
  choice-selected:
    backgroundColor: "{colors.selection-blue}"
    textColor: "{colors.paper-white}"
    rounded: "{rounded.control}"
  title-bar:
    backgroundColor: "{colors.luna-blue}"
    textColor: "{colors.paper-white}"
  caption-button:
    backgroundColor: "{colors.luna-blue}"
    textColor: "{colors.paper-white}"
    rounded: "{rounded.control}"
    size: "32dp"
  dialog-window:
    backgroundColor: "{colors.xp-face}"
    textColor: "{colors.ink-black}"
    rounded: "{rounded.window-top}"
  explorer-list:
    backgroundColor: "{colors.paper-white}"
    textColor: "{colors.ink-black}"
  groupbox-label:
    textColor: "{colors.groupbox-label}"
    typography: "{typography.label}"
  balloon-tip:
    backgroundColor: "{colors.balloon-cream}"
    textColor: "{colors.ink-black}"
    rounded: "{rounded.balloon}"
  game-chip:
    textColor: "{colors.paper-white}"
    rounded: "{rounded.chip}"
---

# Design System: kuma3 Anki XP

## Overview

**Creative North Star: "The Arcade Desktop"**

Anki XP is a faithful Windows XP Luna desktop that hosts a loud little arcade. The frame is 2003 and taken seriously: Luna blue title bars with the light top edge and dark navy text shadow, beige window faces, white Explorer lists, push buttons with the white-to-beige gradient, message boxes with real XP icons, balloon tips, group boxes with their label set into the frame. Inside that calm frame, the reward moments are allowed to shout: gold "+XP" pops, cyan and coral combo chips, a fire-coloured combo counter, a boss card, 8-bit sounds. The contrast between the sober desktop and the bright arcade is the personality.

It is a phone app first. XP supplies the look; phone ergonomics supply the rules: touch targets, contrast measured against the scanlines, and Android Back all win over pixel-faithful XP when they conflict. Density is XP-normal (13-15sp Tahoma, tight 5-8dp gaps), never crammed below a usable target. A CRT scanline layer sits over every window, menu and dialog, in step across windows, and can be turned off.

Rejected outright: Material You shapes (pill toggles, M3 chips, tonal FABs, big rounded cards) and any other Windows era (98 grey, Vista/7 glass, 10/11 flat). If it does not look like it shipped with XP Luna, it does not belong.

**Key Characteristics:**
- Every screen is an XP object: window, message box, Explorer list, group box, Start menu, balloon tip.
- One selection colour (XP selection blue) marks every chosen, active or pressed-on state.
- Calm frame, loud rewards: saturated colour belongs to game moments, not to chrome.
- Tahoma everywhere, bold with a navy shadow on title bars.
- Small corners (3px controls, 8px window tops), 1px hard borders, no soft shadows on controls.
- CRT scanlines over everything, optional.

## Colors

A Luna blue chrome over warm beige faces and white content, with classic Anki count colours and a small arcade palette reserved for the game.

### Primary
- **Luna Blue** (luna-blue): title bars, app bar, tab bar, the Luna strip; the colour that says "XP window".
- **Luna Deep** (luna-deep): status bar and the dark bottom edge of every title bar.
- **Luna Highlight** (luna-highlight): the 2dp light line on top of title bars.
- **Window Frame Blue** (window-frame): the 3px frame of dialogs and web-page windows.
- **Selection Blue** (selection-blue): selected list rows, action mode, active tabs, the chosen scheduler, snackbar actions. The single "this one" colour.
- **Hover Tint** (hover-tint): the current deck row and focused rows; a pale echo of selection.
- **Taskbar Blue** (taskbar-blue): Android navigation bar.
- **Heading Blue** (heading-blue), **Link Blue** (link-blue), **Group Box Label Blue** (groupbox-label): section headings, in-text links (Quests, Trophies) and group box labels.

### Secondary
- **Start Green** (start-green): the add (+) button, as XP's Start button. Pressed: a darker green.
- **Progress Green** (progress-green): XP progress bars, the running combo clock, overtime.

### Tertiary
- **Pop Gold** (pop-gold), **Combo Cyan** (combo-cyan), **Multiplier Coral** (multiplier-coral): game pop-ups and chips only (+XP, combo count, ×multiplier). A broken combo flashes red.
- **Chip Cyan / Chip Coral** (chip-cyan, chip-coral): the fills of the points and ×multiplier chips, dark enough for white text (4.5:1). Gold "+XP" totals sit on a Title Shadow navy chip (9:1); gold never sits on the white card alone.

### Neutral
- **XP Face** (xp-face): window and dialog faces, page background, answer button faces.
- **Face Light** (face-light): surface containers a step lighter than the face.
- **Button Shade** (button-shade): bottom of the push-button gradient; the Show Answer face.
- **Group Box Frame** (groupbox-frame): the thin frame around home group boxes (on white).
- **Group Box Etch** (groupbox-etch): the darker frame of group boxes on the beige face (web pages), with a 1px white inner line so it survives the scanlines.
- **Control Border** (control-border): text fields, lists, switches' edge; XP's blue-grey control line.
- **Button Border** (button-border): the dark navy 1px border of push buttons; also the off-switch border.
- **Balloon Cream** (balloon-cream): balloon tips (snackbars) and the web-page overlay surface.
- **Paper White** (paper-white): list and card content, the reviewer card, Start menu.
- **Ink Black** (ink-black): body text.
- **Muted Text** (muted-text): secondary text on white (7:1).
- **Count New / Learn / Due** (count-new, count-learn, count-due): deck counts, answer button labels (Again red, Good green, Easy blue, Hard black), card states in web pages.

### Named Rules
**The One Selection Rule.** Anything chosen, active or toggled on uses Selection Blue fill with white text. Never a pale tint alone, never a second "selected" colour.

**The Arcade Is Earned Rule.** Gold, cyan, coral and fire colours appear only as feedback to a review answer. Chrome, settings and lists never use them.

## Typography

**Body Font:** XP Tahoma (Wine's free Tahoma, bundled), falling back to Tahoma, then Roboto.
**Title Font:** XP Tahoma Bold.
**Page Heading Font:** XP Tahoma (Trebuchet MS is not on Android).

**Character:** Tahoma is the voice of XP: narrow, plain, slightly technical. Bold Tahoma with a hard navy drop shadow is the title-bar signature.

### Hierarchy
- **Title** (700, toolbar size, navy shadow 2dp offset): Luna title bar text; subtitle in regular Tahoma with a 1.5dp shadow.
- **Body** (400, 15sp): lists, dialog text, dialog buttons, deck names.
- **Label** (400, 13sp): group box labels, links, the game bar, small captions.
- **Page heading** (700, Heading Blue): section titles inside backend web pages.

### Named Rules
**The Tahoma Only Rule.** Native and web surfaces use XP Tahoma; no Roboto, Inter or system fonts visible where Tahoma can load.

## Layout

Phone first at about 1080px wide (DPR ~2.6), tablet second. Screens are stacks of XP containers on the beige face: the home screen is a column of group boxes (Player, Decks, Today, RWKV recall, Activity), each with an 8dp side inset, 20dp head for the inset label and 8dp foot. Message boxes centre their push buttons with 5dp gaps, 88dp minimum width and 38dp minimum height. The reviewer is "maximised": the card fills the screen edge to edge under a 30dp game bar. Lists are full-width white Explorer panes. Backend web pages keep Anki's own layout and are re-skinned, not re-flowed.

## Elevation & Depth

Depth is XP's, not Material's: drawn with gradients and hard 1px lines, not soft blur. Push buttons get depth from a vertical white-to-beige gradient and invert it when pressed; title bars from a light top line and a dark bottom line; status panels are sunken with a grey top-left edge. The only shadows are the navy text shadow on title text and a hard 2px offset shadow under web-page windows. The CRT layer adds a 1-in-3 row of 12% black over the whole window, plus a darker rim on full screens.

### Shadow Vocabulary
- **Title text shadow** (`text-shadow: 2px 2px 1px #0F1089`): Luna title bar text only.
- **Window drop** (`box-shadow: 2px 2px 0 rgba(0,0,0,0.25)`): web-page section windows.

### Named Rules
**The Hard Edge Rule.** No blurred elevation on controls or cards. Depth is a gradient or a 1px line.

## Shapes

Small, square-ish XP corners: 3px on buttons, fields, caption buttons and selection fills; 4px on game chips; 8px on the top corners of windows and dialogs (square bottom corners); 8px all round on balloon tips. Borders are 1px hard lines (3px for window frames). On/off settings are XP check boxes (square, 0 corners), not pills.

## Components

### Buttons
Tactile and a little chunky, unmistakably XP.
- **Shape:** gently rounded (3px), 1px Button Border.
- **Push button:** vertical gradient white → #ECEBE6 → Button Shade, black Tahoma text; dialog buttons 88×38dp minimum.
- **Pressed:** the gradient inverts (#CDCAC3 → #E3E3DB → #F2F2F1) with a faint Selection Blue ripple.
- **Answer buttons:** one push-button look for all four; the label colour tells them apart (Again red, Hard black, Good green, Easy blue). The labels use darker shades of the count colours (answer-again, answer-good, answer-easy) so they reach 4.5:1 on the darkest stop of the button gradient.
- **Caption buttons:** 32dp blue rounded squares with a white 1px edge and a light top-left gradient, behind every title-bar action.

### Tabs and Choice Groups
- **Unselected:** push button.
- **Selected:** Selection Blue fill, white text (Preset / This deck / Today only, scheduler choices). At least 32px tall.

### Cards / Containers
- **Group box (home):** 1px Group Box Frame, label in Group Box Label Blue 13sp cut into the top edge.
- **Group box (web pages, e.g. deck options):** an XP Properties sheet. Transparent on the face, 1px Group Box Etch with a white inset line, 3px corners, 20/12/10px padding; the section title (16px Tahoma, Group Box Label Blue) and the help badge (Heading Blue ?, 32px target) sit in the top edge on a face-coloured patch.
- **Window (dialogs, congrats):** XP Face body, 3px Window Frame Blue, 8px top corners.
- **Explorer list:** white with a 1px Control Border edge.

### Check Boxes and Trackbars
- **Check box:** every on/off setting (web switches included): 24px square, 1px #1C5180 edge, diagonal #DCDCD7 to white face, green (#21A121) tick when on; the label toggles it too.
- **Option button (radio):** 18px circle, same edge and face as the check box, green (#21A121) dot when chosen; label rows 32px tall at 13px, wrapping between labels.
- **Trackbar:** a 4px sunken groove (grey top-left, white bottom-right edges) with a 14x26px push-button thumb.

### Inputs / Fields
- **Style:** white field, 1px Control Border, 3px corners.
- **Focus:** Selection Blue border.
- **Combo box (drop-down):** a white field with Anki's arrow button.
- **Revert icon (web):** Muted text colour on a 32px target.

### Navigation
- **Title bar:** Luna gradient with highlight and deep edges, bold shadowed Tahoma, caption-button actions; system Back always works.
- **Side menu:** the Start menu's white list with an XP header image; checked item in Heading Blue.

### At a Glance (Statistics)
The quick answer at the top of Statistics: a group box of four read-only XP fields (white, 1px Control Border), each a 24px bold number over a 13px label, 2 by 2. Retention is Count Learn red below the deck's target and Count Due green on it, with the target written out.

### Message Box and Balloon Tip (signature)
Alerts are XP message boxes: title bar, a drawn XP icon (info, question, warning, error), centred push buttons. Snackbars are cream balloon tips with a black 1px border and Selection Blue actions.

### Game Layer (signature)
The game bar sits under the title bar with the finish lines (reps to the daily goal, "goal ✓" once met; cards left today, the deck list's number), streak, level, XP bar, combo and the speed-bonus clock; it is at least 30dp and grows with the font size, and TalkBack reads its state (a polite live region). Pop-ups rise from the answer buttons: a points chip and a ×multiplier chip (white text, 2px black border, 4px corners), then the gold "+XP" total on a navy chip, a fire-gradient combo counter, a red "COMBO BROKEN" (only after 5 minutes idle). Fast, loud and over in about a second. A multiplier step shakes and flashes only the game bar, never the answer buttons. With the system's Remove animations on, the shake, white flash, juice bounce and flame flicker stay still; game sounds stay silent when the ringer is on silent or vibrate. Balloons stack by their real heights and are read out as they appear.

## Do's and Don'ts

### Do:
- **Do** build every new surface out of XP objects (window, group box, message box, Explorer list, balloon tip) before inventing anything.
- **Do** mark every chosen or active state with Selection Blue fill and white text.
- **Do** keep touch targets at 32dp+ for settings controls and 38dp+ for dialog buttons, even when real XP was smaller.
- **Do** check contrast on a scanline row (about #E1E1E1 on white, #C4C2B4 on the face), not on plain white.
- **Do** style backend web pages only through Anki's CSS variables and generic elements, and exclude Anki's small icon buttons (revert, help) from the push-button frame.
- **Do** keep game colour and motion for answer feedback.
- **Do** leave chart bar colours to Anki (d3 sets them in code); make chart text Tahoma, fully opaque, #444.

### Don't:
- **Don't** use Material You shapes: pill toggles, M3 chips, tonal FABs, large rounded cards.
- **Don't** mix in another Windows era: no 98 grey bevels, Vista/7 glass, or 10/11 flat styling.
- **Don't** use soft blurred shadows or gradients outside the XP vocabulary above.
- **Don't** show Roboto where Tahoma can load.
- **Don't** let XP styling hide state: an active tab, a chosen option or a modified value must stay visible.
