# JellyfinEasyMetadataManager

Jellyfin Easy Metadata Manager (Also called as "JEMM") is a free and open-source desktop tool created to help heavy users of Jellyfin Project (https://jellyfin.org/) to manage their instances.
The main purpose of JEMM is to make it easier to fill in the metadata fields of library-items and libraries. 

Recommended for managing the metadata fields of private content not available on IMDb or TheMovieDb sources.

With JEMM, you can easily replicate the same metadata fields defined in a library to the child-items, export and import metadata from CSV files, print some reports about your media-inventory and others nice things! You can make just a little metadata update or make big changes massivelly!

## About this fork
This is a public fork of the original open-source JEMM. It keeps all the original functionality and adds a few batch tools plus some behavior fixes. All new tools live under the **Tools** menu and always work on the libraries selected in the left "Libraries" list, including their subfolders (recursive).

### New tools
- **Auto Tags** – Automatically adds tags based on the media's technical info: orientation (`vertical` / `horizontal` / `square`), frame rate (`low fps` / `standart fps` / `high fps`, videos only), resolution (`SD` / `HD` / `FULL HD` / `2K` / `4K`, plus `ULTRA RES` for very large images) and a quality rating (`QR1`, `QR2`, ...) derived from resolution vs. bitrate. It only adds/updates its own computed tags and never touches your manual tags.
- **Metadata Cleaner** – Bulk-clears selected metadata (Tags, People, Genres, Studios and/or Preferred language & country) so you can start clean before applying new logic. Optionally also cleans the folders themselves, not just the media.
- **Episode Namer** – Assigns sequential episode-style names (e.g. `<base> - EP01`, `EP02`, ...). The base can be each folder's name or a custom prefix, numbering restarts per folder, and you choose whether to set the Name and/or the Original Title & Sort Name. This is now opt-in (see below).
- **Name Cleanup** – Reverts the episode naming again: removes a trailing `" - EP##"` from the Name (or resets the Name from the file name) and can clear the Original Title & Sort Name.

### Quality of life
- **Remembered login.** After a successful login the Jellyfin server URL and API key are stored (via Java Preferences) and pre-filled on the next start, so you don't have to type them in every time.
- **Detail grids follow keyboard navigation.** In the "Library Content" tab the People/Genres/Studios/Tags panels now update as you move the selection with the arrow keys. Previously they kept showing the item you had clicked before, which was confusing since the selection moved with the keys.

### Changed / removed behavior
- **Multi-selection everywhere.** "Apply Changes" in the "Library Content" tab now applies to every selected item (the original only handled a single item). Likewise, the tools and the left "Libraries" list act on all selected libraries, including their subfolders. When applying to several items, People/Genres/Studios/Tags are **merged** onto each item, not overwritten (see below).
- **Optional fields are truly optional.** Fields that JEMM used to require but that Jellyfin does not require are no longer mandatory in JEMM, so you can save without filling them in.
- **Created-date bug fixed.** A folder's created date is no longer taken from its premiere date; both dates are handled independently.
- **List metadata is merged, not replaced.** Applying People/Genres/Studios/Tags now merges (de-duplicated) with what the item already has instead of overwriting it. In the "Library Content" tab, "Apply Changes" affects only the selected items.
- **No more forced language/region.** The tool no longer forces the preferred metadata language/country to `pt-br` / `BR`; existing values are kept. (The Metadata Cleaner can reset old `pt-br` / `BR` values if you want.)
- **Automatic episode titles were removed** from "Apply for Library and Content". That naming is now the opt-in **Episode Namer** tool, so untouched items keep their names.
- **Content list loads full metadata** (People/Genres/Studios/Tags) so those fields display and merge correctly.

### Known limitation
On some Jellyfin server versions (e.g. 10.11.x, see jellyfin/jellyfin issue #17142) **adding** cast/crew (People) is not persisted by the server itself – this affects the official web client too and cannot be worked around from JEMM. Everything else (tags, genres, studios, ...) saves normally.

## To download a runnable file
If you are not a developer or do not want to download the complete source-code and build it locally, you can do directly download the runnable file [jemm_runnable-jar-with-dependencies.jar](target/jemm_runnable-jar-with-dependencies.jar) (available at: root/target/jemm_runnable-jar-with-dependencies.jar). 
**Java version 11 is required on your machine and you will need able an Api Key - through the "Administration options" in your Jellyfin instance.**

## To build locally
If you are a developer, you can do download the project directly using your preferred git client. 
JEMM was built using Apache NetBeans IDE 19 and Java version 11, but you can use your preferred IDE.

## To create an Api Key in your Jellyfin Instance
1. Once in Jellyfin, navigate to the Jellyfin Dashboard by clicking the "hamburger" icon in the top left corner and click on Admin > Dashboard. 
2. In the left Navigation menu, scroll down to Advanced and click on Api Keys. 
3. Now click on the + button next to API Keys.

## To get help

Looking for detailed guidance on how to use JEMM?
Check out the [JEMM User Manual](https://cesarbianchi.github.io/JellyfinEasyMetadataManager/) for step-by-step instructions, feature explanations, and best practices to get the most out of your Jellyfin Easy Metadata Management.

Ideal for both beginners and advanced users!

## To contribute to this project
If you are a developer and want to contribute to this project (Bug Fixes, New Features, Translation, Documentation, etc.), please write to cesar_bianchi@hotmail.com.

If you want pay me a coffee or help me to pay some costs (certificates and others things), please consider [make some donation to me](https://www.paypal.com/donate/?hosted_button_id=SUBJ5D8KVC6ZN)

## About Jellyfin Project
Jellyfin is the volunteer-built media solution that puts you in control of your media. 
Stream to any device from your own server, with no strings attached. Your media, your server, your way.

Please, visit the Project website available in: https://jellyfin.org/
Also visit the community forum available at: https://forum.jellyfin.org/
