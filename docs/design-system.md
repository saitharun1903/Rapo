# Raido design system

The Raido interface is:
- minimal;
- high contrast;
- built around the map;
- quiet in motion.

It should read as mobility technology, not as a generic dashboard. The tokens live in
[`frontend/src/app/globals.css`](../frontend/src/app/globals.css); this document explains them and the rules for
using them.

## Principles

1. **The map is the product.** Panels float over it or sit beside it; nothing covers more of it than it needs to.
2. **One signal colour.** Orange marks what is live: the route, the moving car, the brand dot, the action that
   starts something live. It is not used for decoration.
3. **Ink for decisions.** The main action on a screen is ink (near-black in light mode, near-white in dark mode).
   There is one per screen.
4. **Status is never colour alone.** Success, warning and danger always carry an icon or words.
5. **Honest emptiness.** An empty state says why it is empty and what would change that. No zeros standing in for
   "unknown", no invented numbers.
6. **Motion explains a change.** Something moves only when the state changed: a driver was assigned, the trip
   started. Nothing loops except the live indicators, and those stop under reduced motion.

## Colour

A single warm-neutral grey family, in both themes.

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#f4f4f1` | `#0f100e` | Page background |
| `surface` | `#ffffff` | `#171815` | Panels, sheets, inputs |
| `surface-2` | `#ecece8` | `#20211e` | Hover, wells, segmented tracks |
| `line` / `line-strong` | `#deded8` / `#c9c9c1` | `#2c2d29` / `#3b3c37` | Hairlines / input borders |
| `fg` / `fg-muted` | `#121311` / `#5f6059` | `#ecece6` / `#a1a298` | Text |
| `ink` / `ink-fg` | `#121311` / `#fafaf7` | `#ecece6` / `#121311` | Primary action |
| `brand` | `#e5561f` | `#ff7440` | Signal: route, live car, brand dot, *signal* buttons (with `brand-fg` text) |
| `brand-strong` | `#b13f11` | `#ff9368` | Signal as **text** (meets 4.5:1 on `surface`) |
| `brand-soft` | `#fcebe3` | `#3a1c0e` | Signal wash |
| `success`, `warning`, `danger` (+ `-soft`) | — | — | Status only |

**Contrast rules.**
- Orange text uses `brand-strong`. The base `brand` is for fills, strokes and icons (at least 3:1).
- Primary buttons are ink on paper or paper on ink, well above 7:1.

## Type

| Role | Font | Settings |
|---|---|---|
| Interface | Geist | `ss01`, `cv11`; weights 400, 500, 600 |
| Figures (fares, ETAs, distances, plates) | Geist Mono, or Geist with the `num` utility (tabular figures) | Numbers in columns always align |

| Level | Size / weight / tracking |
|---|---|
| Display (website) | 3.5–5 rem, 600, −0.045 em, leading 1.02 |
| Page title | 1.75 rem, 600, −0.03 em |
| Panel headline (ride state) | 1.35 rem, 600, −0.02 em |
| Body | 0.875–1 rem, 400 |
| Eyebrow (`eyebrow` utility) | 0.75 rem, 500, sentence case, muted |

Headings use `text-wrap: balance` and paragraphs `text-wrap: pretty`. Text is written in sentence case; labels are
never in capitals.

## Shape, depth and layers

| Token | Value | Use |
|---|---|---|
| `rounded-control` | 10 px | Buttons, inputs, list rows |
| `rounded-card` | 14 px | Cards, popovers, tables |
| `rounded-sheet` | 20 px | Bottom sheets, dialogs, panels over the map |
| `shadow-raise` | Tinted, tight | Controls over content (map buttons, segmented thumb) |
| `shadow-float` | Tinted, wide | Anything floating over the map, popovers, dialogs |

Cards have a hairline and no shadow; shadows are only for things that float.

The layers, lowest first, are:
1. map controls (10);
2. floating panels (20);
3. header (40);
4. sheets and popovers (50);
5. toasts (60).

Dialogs use the native top layer.

## Motion

| Name | Duration and easing | Use |
|---|---|---|
| `animate-rise` | 280 ms, `ease-out-soft` | A panel or card that appears because of a state change (driver assigned, menu opened) |
| `animate-fade` | 200 ms | Banners, ride-state cross-fade |
| `animate-search` | 2.4 s loop | The matching ring around the pickup, only while MATCHING |
| Press | `scale(0.98)` | Every button |

Under `prefers-reduced-motion`, every animation and transition is cut to a single frame and map camera moves
become instant.

## Components

| Component | File | Notes |
|---|---|---|
| `Button`, `ButtonLink` | `components/ui/button.tsx` | Variants: `primary` (ink), `signal` (orange, for starting something live), `secondary`, `ghost`, `danger` |
| `IconButton` | same | Always has a `label`; `raised` tone for map controls |
| `Card`, `Stat`, `KeyValue`, `Badge`, `EmptyState`, `ErrorState`, `Skeleton`, `PageHeader` | `components/ui/surface.tsx` | `Stat` values use tabular figures |
| `Field`, `Input`, `Select`, `Textarea` | `components/ui/form.tsx` | Label, hint and error wired to `aria-describedby` |
| `Segmented` | `components/ui/segmented.tsx` | A radio group |
| `Dialog` | `components/ui/dialog.tsx` | Native `<dialog>` |
| `BottomSheet` | `components/ui/sheet.tsx` (R3) | Mobile ride panel with snap points; a floating panel from `lg` |
| App shell | `components/layout/AppShell.tsx` | Underlined navigation, a live indicator, an account menu with the theme choice, and a connection banner (offline, reconnecting, unavailable, restored) |

## Map

The map styling (light and dark styles, restyled roads, water and labels, and the markers) is specified in
[feature-spec.md §2](feature-spec.md#2-map-p0) and built in R5. Markers follow the same rules:
- **Pickup:** an ink ring.
- **Destination:** an ink square.
- **Car:** orange with a heading wedge and a soft halo.
- **Stale car:** grey.

## Voice

- **Plain and specific.** "Your driver is here", not "Your ride has arrived!".
- **No exclamation marks.** Also none of these words: "seamless", "elevate", "unleash", "next-gen".
- **Safety prompts are questions, never accusations.** "We noticed an unusual stop. Are you okay?"
- **Errors say what happened and what to do.** "Couldn't reach Raido. Check your connection and try again."
