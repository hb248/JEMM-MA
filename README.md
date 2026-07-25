# JellyfinEasyMetadataManager-ManualAdditon

**JEMM-MA** is a public fork of [Jellyfin Easy Metadata Manager (JEMM)](https://github.com/CesarBianchi/JellyfinEasyMetadataManager).

JEMM-MA adds QoL improvements to the overall experience and tries to improve the workflow for building library metadata from scratch — home videos, private content.

> **MA = Manual Addition.** Like an “edition”: same base product, tuned for a specific workflow — you supply the metadata yourself instead of relying on TMDB, IMDb, or other scrapers.

## Why this fork?

Original JEMM already helps with private content that isn’t on public databases. **JEMM-MA** doubles down on that: start (or reset) a library at zero and build tags, names, people, genres, and studios under your control.

Typical use cases:

- Home videos and other private media with no external metadata source
- Custom tagging logic you define yourself (technical tags, house rules, …)
- Clearing scraped or leftover metadata so you can start clean

All of the original JEMM functionality remains. New batch tools live under the **Tools** menu and always work on the libraries selected in the left **Libraries** list, including their subfolders (recursive).

## Upstream

| | |
|---|---|
| **This fork** | [hb248/JEMM-MA](https://github.com/hb248/JEMM-MA) |
| **Upstream** | [CesarBianchi/JellyfinEasyMetadataManager](https://github.com/CesarBianchi/JellyfinEasyMetadataManager) |
| **Upstream manual** | [JEMM User Manual](https://cesarbianchi.github.io/JellyfinEasyMetadataManager/) |

Credit and thanks to [Cesar Bianchi](https://github.com/CesarBianchi) for the original JEMM project.

## What’s different in JEMM-MA

### New tools

- **Auto Tags** – Adds tags from technical media info: orientation (`vertical` / `horizontal` / `square`), frame rate (`low fps` / `standart fps` / `high fps`, videos only), resolution (`SD` / `HD` / `FULL HD` / `2K` / `4K` / `6K` / `8K` / `ULTRA RES`, the same ladder for videos and images), `No Audio` for silent videos, and a linear quality rating (`QR0`, `QR1`, `QR2`, …) from data density per megapixel — bitrate for videos (normalized to the standard 24–30 fps range so low/high-fps clips aren't mis-rated) and file size x4 for images. Each category (orientation, resolution, frame rate, quality rating, no-audio) can be enabled/disabled with checkboxes; disabled categories are left untouched. Only its own computed tags are added/updated; your manual tags are left alone. When Jellyfin's API doesn't expose the technical data, an optional **ffprobe** fallback reads it directly from the media — see below.
- **Metadata Cleaner** – Bulk-clears selected metadata (Tags, People, Genres, Studios, preferred language & country, and/or Community & Critic ratings reset to 0) so you can start clean before applying new logic. Tags can be cleared completely or, with the *only auto-tags* option, limited to the tags produced by Auto Tags so your manual tags survive. Optionally also cleans the folders themselves, not just the media.
- **Episode Namer** – Assigns sequential episode-style names (e.g. `<base> - EP01`, `EP02`, …). The base can be each folder’s name or a custom prefix; numbering restarts per folder; you choose Name and/or Original Title & Sort Name. Opt-in only (see below).
- **Name Cleanup** – Reverts episode naming: removes a trailing `" - EP##"` from the Name (or resets the Name from the file name) and can clear Original Title & Sort Name.
- **Tag-Team Mode** – A guided, keyboard-fast pass over the selected libraries that walks user-defined tag/genre decision trees (your "tag map") for each folder and file, with always-on Actors / Studios / Release-date panels and filename-based suggestions. See below.

#### Tag-Team Mode

Tag-Team Mode turns tagging into a quick, click-through workflow. It walks every **stop** in the selected libraries — each folder, then its files, recursively — and for each stop lets you click through your **tag map** (decision trees) while editing actors, studios and the release date on the side.

- **One active tag map.** Edit it in **Tools → Tag Map Editor…** (tree outline + detail form + read-only structure preview with arrows). JSON remains fully supported via **Tools → Import Tag Map…** / **Export Tag Map…** (next to Metadata CSV Import/Export). The Tag-Team start dialog shows the active map and offers **Edit map…**. The map is stored as the single active file (`~/.jemm/tagmap.json` by default).
- **Fast walk.** At start, stop metadata and the Jellyfin people/studios catalogs are preloaded. During the walk, applies stay **in memory**; **Finish & Close** (or Save when closing the window) POSTs all pending changes. Closing with unsaved work asks Save / Discard / Cancel.
- **Decision trees.** A map has one or more named trees, walked in `order`. Each node is a chip you can click; a node may assign one or several tags/genres and/or lead to deeper chips. Single-select nodes descend automatically; multi-select nodes let you pick several children and then queue those branches in turn.
- **Skips.** Skip the current tree, the current file, a whole folder (you still visit its files), or the rest of the current folder.
- **File-type filter.** Choose up-front whether to walk videos only, videos + images, or all files.
- **Side panels.** Actors (with a type dropdown that defaults to *Actor*), Studios and Release date are always visible, pre-filled from the item, and editable/removable. Entries are listed with their type (`Alice (Actor)`, `Alice (Director)`). Live autocomplete uses the Jellyfin people/studios catalog; Name+Type duplicates are rejected. Filename suggestions appear inline in a distinct color; click one to promote it.
- **Canonical title suggestion.** The suggested Name follows `[Studio1 | Studio2] Actor1, Actor2 - Videotitel (YYYY-MM-DD)` and rebuilds live as you edit studios, actors (Type=Actor only in the cast segment), core title, and the date field. Click the chip to copy it into *Set title*. Hash-like file titles fall back to the parent folder name for the Videotitel core.
- **Filename suggestions.** Names like `[Studio]actor - title (date)` are parsed into Studio/Actor/Title/Date suggestions; the catalog reclassifies known studios that would otherwise be treated as actors (e.g. `StudioX - Alice`). Ambiguous dates (e.g. `123022`) are pruned to the valid readings.
- **Folder stops.** Tagging a folder applies your choices to the folder itself **and** its direct contents as a base; an *Also apply to nested subfolders* checkbox cascades that base into nested subfolders and their contents too.
- **Overwrite rule.** Only tag/genre values **owned by the trees you actually walked** are replaced. Manual tags, Auto Tags values, and values owned by trees you skipped are never touched. Actors/studios are set from the panel (merged into children on folder stops); the date fills in and (on the current item) updates `PremiereDate` + `ProductionYear`.
- **Jellyfin link.** *Open in Jellyfin* opens the current item in the Jellyfin web UI.
- Number keys pick chips; **Enter** submits an actor and confirms a multi-select.

A ready-to-edit example map ships as [`tagmap.example.json`](tagmap.example.json). The JSON schema is:

```jsonc
{
  "version": 1,
  "trees": [
    {
      "name": "Type",           // tree name (required, unique)
      "order": 1,                // walk order (ascending)
      "multiSelect": false,      // may several of this tree's chips be picked at once?
      "children": [
        {
          "label": "Solo",       // chip text
          "multiSelect": false,  // may several of THIS node's children be picked?
          "assign": [            // tags/genres this chip sets (optional; empty = traversal only)
            { "kind": "tag",   "value": "solo" },
            { "kind": "genre", "value": "Solo Scene" }
          ],
          "children": []         // deeper chips reached after picking this one
        }
      ]
    }
  ]
}
```

#### ffprobe fallback for Auto Tags

Auto Tags first uses the technical data Jellyfin already exposes (MediaStreams / item Width & Height). If that data is missing for an item, it can optionally fall back to **[ffprobe](https://ffmpeg.org/ffprobe.html)** to read the real width, height, frame rate, bitrate and file size straight from the media — so orientation/resolution are always correct instead of skipped.

- Enable it with the **Use ffprobe as fallback** checkbox in the Auto Tags dialog (on by default; the setting is remembered).
- ffprobe is expected on your system **PATH**; you can also point to a specific binary via the path field.
- Input is chosen automatically: the item's **local file path** when it is reachable from the machine running JEMM, otherwise the Jellyfin **download URL**.
- If ffprobe isn't available, Auto Tags simply uses the API-only data and reports it in the summary.
- An optional second checkbox lets you fall back to the **poster/aspect-ratio hint** for orientation only when ffprobe is unavailable (the ffprobe issue is still reported). This is a best-effort guess; resolution and other tags still require real dimensions.

### Quality of life

- **Remembered login.** After a successful login, the Jellyfin server URL and API key are stored (Java Preferences) and pre-filled on the next start.
- **Detail grids follow keyboard navigation.** In the **Library Content** tab, the People/Genres/Studios/Tags panels update when you move the selection with the arrow keys (previously they stayed on the last clicked item).

### Changed / removed behavior

- **Multi-selection everywhere.** **Apply Changes** in **Library Content** applies to every selected item (upstream handled a single item). Tools and the **Libraries** list act on all selected libraries, including subfolders. When applying to several items, People/Genres/Studios/Tags are **merged**, not overwritten.
- **Optional fields are truly optional.** Fields that JEMM used to require but Jellyfin does not are no longer mandatory, so you can save without filling them in.
- **Created-date bug fixed.** A folder’s created date is no longer taken from its premiere date; both are independent.
- **List metadata is merged, not replaced.** Applying People/Genres/Studios/Tags merges (de-duplicated) with what the item already has. In **Library Content**, **Apply Changes** affects only the selected items.
- **No more forced language/region.** Preferred metadata language/country is no longer forced to `pt-br` / `BR`; existing values are kept. (Metadata Cleaner can reset old `pt-br` / `BR` values if you want.)
- **No more hardcoded ratings.** Upstream JEMM forced Community and Critic rating to `10` on folder Apply even when you never touched those fields. JEMM-MA leaves existing ratings alone; use **Metadata Cleaner** if you want them reset to `0`.
- **No more silent Forced Sort Name rewrites.** Upstream save paths often copied Original Title (or the Name) into `ForcedSortName` as a side effect of unrelated updates. JEMM-MA posts the sort name you actually have (or leave empty). Only **Episode Namer** sets Original Title / Forced Sort Name when you opt into that checkbox.
- **Automatic episode titles were removed** from “Apply for Library and Content”. That naming is now the opt-in **Episode Namer**, so untouched items keep their names.
- **Content list loads full metadata** (People/Genres/Studios/Tags) so those fields display and merge correctly.

### Known limitation

On some Jellyfin server versions (e.g. 10.11.x, see [jellyfin/jellyfin#17142](https://github.com/jellyfin/jellyfin/issues/17142)) **adding** cast/crew (People) is not persisted by the server itself — this also affects the official web client and cannot be worked around from JEMM. Everything else (tags, genres, studios, …) saves normally.

---

## About upstream JEMM

Jellyfin Easy Metadata Manager (**JEMM**) is a free and open-source desktop tool for heavy Jellyfin users. It makes it easier to fill in metadata for libraries and items — especially private content not available on IMDb or TheMovieDb.

With JEMM you can replicate library metadata to child items, export/import CSV, print inventory reports, and make small or large metadata updates in bulk.

## Download a runnable file

If you do not want to build from source, download [jemm_runnable-jar-with-dependencies.jar](target/jemm_runnable-jar-with-dependencies.jar) (path: `target/jemm_runnable-jar-with-dependencies.jar`).

**Java 11** is required. You also need an API key from your Jellyfin instance (**Administration** → API Keys).

## Build locally

Clone this repository with your preferred Git client. JEMM was built with Apache NetBeans IDE 19 and Java 11; any suitable IDE works.

## Create an API key in Jellyfin

1. Open the Jellyfin Dashboard (hamburger menu → Admin → Dashboard).
2. Under **Advanced**, open **API Keys**.
3. Click **+** next to API Keys.

## Help

- Fork-specific tools and behavior: see **What’s different in JEMM-MA** above.
- General JEMM usage: [upstream User Manual](https://cesarbianchi.github.io/JellyfinEasyMetadataManager/).

## Contributing

Issues and contributions for **JEMM-MA** belong in this repository.

For the original JEMM project (bugs/features against upstream), contact [cesar_bianchi@hotmail.com](mailto:cesar_bianchi@hotmail.com) or use the [upstream repo](https://github.com/CesarBianchi/JellyfinEasyMetadataManager).

If you want to support the original author: [donate via PayPal](https://www.paypal.com/donate/?hosted_button_id=SUBJ5D8KVC6ZN).

## About Jellyfin

Jellyfin is the volunteer-built media solution that puts you in control of your media. Stream to any device from your own server, with no strings attached.

- Website: https://jellyfin.org/
- Forum: https://forum.jellyfin.org/
