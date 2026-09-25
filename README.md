# JAVDB CLI

JAVDB is a local SQLite catalog for a video collection. The default database is:

```text
data/javdb.db
```

Use `--database <path>` with any command to use a different database.

## Run with Gradle

```bash
./gradlew run
./gradlew run --args='--database build/test-cli.db publisher list'
```

Running with no arguments starts the interactive menu. Supplying arguments runs one command and exits.

## Distribution

```bash
./gradlew installDist
```

The generated Unix executable is:

```text
build/install/JAVDB/bin/JAVDB
```

The generated Windows executable is:

```text
build/install/JAVDB/bin/JAVDB.bat
```

## Exit Codes

```text
0  success
2  usage error
3  validation error
4  missing record
5  database error
6  file or input error
7  partial batch failure
```

## Commands

```text
javdb help
javdb --help
javdb init [--database <path>]
javdb gui [--database <path>]
javdb gui --help

javdb publisher add --name <name> [--alias <alias>]...
javdb publisher list

javdb performer add --name <main-name> --category <category> [--alias <alias>]...
javdb performer list

javdb series add --title <title> --publisher <publisher-uuid>
javdb series list

javdb media add --path <file-path> [--file-size <bytes>] [--duration <millis>] [--width <px>] [--height <px>] [--hash <hash>] [--last-modified <millis>]
javdb media list
javdb media show --id <media-uuid>
javdb media scan --root <directory> [--ffprobe <path>] [--hash] [--dry-run] [--fail-fast] [--extension <ext>]... [--output tsv]
javdb media verify [--ffprobe <path>] [--refresh] [--dry-run] [--fail-fast] [--output tsv]
javdb media probe-check [--ffprobe <path>]
javdb media unassigned [--contains <text>] [--directory <path>] [--width <px>] [--height <px>] [--min-width <px>] [--min-height <px>] [--limit <count>] [--offset <count>] [--output tsv]
javdb media assignment --media <media-uuid> [--output tsv]
javdb media parse-preview [--media <media-uuid>]... [--all-unassigned] [--contains <text>] [--directory <path>] [--width <px>] [--height <px>] [--min-width <px>] [--min-height <px>] [--limit <count>] [--offset <count>] [--output tsv]
javdb media rename-preview --media <media-uuid> [--scene <scene-uuid>] [--movie <movie-uuid>] [--output tsv]
javdb media rename --media <media-uuid> [--scene <scene-uuid>] [--movie <movie-uuid>] [--dry-run] [--output tsv]

javdb scene add --title <title> --publisher <publisher-uuid> [--code <code>] [--release-date <yyyy-MM-dd>] [--series <series-uuid>] [--season <season>] [--episode <episode>] [--performer <uuid>]... [--media <uuid>]...
javdb scene list
javdb scene show --id <scene-uuid>
javdb scene search-by-performer --performer <performer-uuid>
javdb scene attach-media --scene <scene-uuid> --media <media-uuid>
javdb scene create-from-media --media <media-uuid> --publisher <publisher-uuid> [--title <title>] [--code <code>] [--release-date <yyyy-MM-dd>] [--series <series-uuid>] [--season <season>] [--episode <episode>] [--performer <uuid>]... [--output tsv]
javdb scene create-from-media --media <media-uuid>... --publisher <publisher-uuid> [--dry-run] [--fail-fast] [--output tsv]
javdb scene create-from-media-batch --input <csv-file-or-dash> [--dry-run] [--fail-fast] [--output tsv]
javdb scene auto-index [--media <media-uuid>]... [--all-unassigned] [--contains <text>] [--directory <path>] [--limit <count>] [--offset <count>] [--dry-run] [--fail-fast] [--output tsv]
javdb scene index-review [--media <media-uuid>]... [--all-unassigned] [--contains <text>] [--directory <path>] [--limit <count>] [--offset <count>]
javdb scene verification list [--status unverified|verified|needs-review] [--limit <count>] [--offset <count>] [--output tsv]
javdb scene verification set --scene <scene-uuid> --status unverified|verified|needs-review [--output tsv]
javdb scene rename-media --scene <scene-uuid> [--movie <movie-uuid>] [--dry-run] [--fail-fast] [--output tsv]

javdb movie add --title <title> --publisher <publisher-uuid> [--release-date <yyyy-MM-dd>] [--compilation <true|false>] [--scene <uuid>]... [--media <uuid>]...
javdb movie list
javdb movie show --id <movie-uuid>
javdb movie attach-media --movie <movie-uuid> --media <media-uuid>

javdb backup create --destination <backup-path> [--overwrite] [--verify quick|full] [--output tsv]
javdb backup verify --input <backup-path> [--level quick|full] [--output tsv]
javdb backup restore --input <backup-path> --destination <new-database-path> [--overwrite] [--level quick|full] [--output tsv]
```

Repeated `--scene` options define movie scene order.

## TSV Output

Use:

```bash
--output tsv
```

TSV output includes a stable header row and one record per line. Tabs and newlines inside fields are escaped as `\t` and `\n`. Errors still go to standard error.

Example:

```bash
./gradlew run --args='--database build/test-cli.db publisher list --output tsv'
```

Media scan TSV columns are:

```text
status,media_id,path,file_size,last_modified_millis,width,height,duration_millis,content_hash,duplicate_media_id,duplicate_path,error
```

Media verify TSV columns are:

```text
status,media_id,path,stored_file_size,actual_file_size,stored_last_modified_millis,actual_last_modified_millis,error
```

Unassigned media TSV columns are:

```text
media_id,path,file_size,last_modified_millis,width,height,duration,content_hash
```

Assignment TSV columns are:

```text
media_id,assignment_type,target_id,target_title
```

Scene-from-media batch TSV columns are:

```text
row,status,media_id,path,title,scene_id,error
```

Filename preview TSV columns are:

```text
status,media_id,path,release_date,title,publisher_id,publisher_name,series_id,series_title,movie_id,movie_title,performer_ids,performer_names,unresolved_segments,alternative_count,warnings,error
```

Auto-index TSV columns are:

```text
status,media_id,path,scene_id,title,release_date,publisher_id,publisher_name,series_id,series_title,movie_id,movie_title,movie_scene_order,performer_ids,performer_names,code,season,episode,warnings,error
```

Scene verification TSV columns are:

```text
scene_id,verification_status,title,release_date,publisher_id,publisher_name,series_id,series_title,media_count
```

Media rename TSV columns are:

```text
status,media_id,scene_id,original_path,proposed_path,selected_movie_id,selected_movie_title,movie_selection_source,verification_status,warnings,error
```

Backup creation TSV columns are:

```text
status,source,destination,file_size,schema_version,verification_level,verification_status,created_at,error
```

Backup verification TSV columns are:

```text
status,path,file_size,schema_version,verification_level,integrity_status,foreign_key_status,missing_tables,messages,error
```

Restore TSV columns are:

```text
status,input,destination,file_size,schema_version,verification_level,verification_status,error
```

## JavaFX Review Queue

Launch the JavaFX review workflow with:

```bash
build/install/JAVDB/bin/JAVDB gui
build/install/JAVDB/bin/JAVDB gui --database "/path/to/javdb.db"
./gradlew run --args='gui --database build/gui-test.db'
```

The GUI opens a JavaFX window titled `JAVDB`. It initializes the selected
database, displays that database path in the window, and loads one page of
unassigned media at a time. A media file is unassigned when it is attached to
neither a scene nor a movie.

The review screen has two queues:

```text
Unassigned Media
Unverified Scenes
```

The same tab area also includes a read-only `Media Library` for scan review.

Unassigned media rows are for files that need a new scene. Unverified scene rows
are for existing scenes whose verification status is `UNVERIFIED` or
`NEEDS_REVIEW`.

The queue supports database-backed filters for filename/path text, directory
prefix, exact width, exact height, minimum width, and minimum height. Pagination
uses SQL limit and offset so large collections are not loaded into memory. The
match-status selector is labeled `Status on this page` because it filters only
the currently loaded database page in this initial version.

Queue columns include filename, match status, proposed title, publisher,
series, original movie, performers, resolution, duration, and warning count.
Selecting a row displays details for media metadata, structural parse data,
matched interpretation, alternatives, warnings, and canonical filename
readiness. The editor lets you correct title, release date, code, season,
episode, publisher, series, original movie, and performers before saving.

Canonical filename preview uses the current unsaved editor values. Changes to
title, date, code, season, episode, publisher, series, movie, movie override,
and performers trigger background recomputation for the active draft. Preview
does not create temporary scene records. Ambiguous and unresolved rows do not
receive guessed filenames. The preview reports unchanged paths, physical
destination collisions, database path conflicts, review-required movie
selection, and validation errors without moving files or updating records.

Reviewed saves use explicit actions:

```text
Save Without Rename
Save and Rename
Save as Needs Review
Skip
Reset Changes
```

Saving without rename is valid and remains available when a rename is blocked by
collision, missing source file, database path conflict, review-required movie
selection, or an unchanged filename. Save-and-rename is enabled only when the
latest preview is current and safe to apply. It uses the same safe rename
service as the CLI, never overwrites destination files, and keeps the file in
the same directory. If metadata is saved but a requested rename fails, JAVDB
preserves the scene and forces `NEEDS_REVIEW`.

Unknown entities and aliases are never created silently. Publisher, performer,
series, and movie creation are explicit review actions. Series and movie dialogs
use bounded publisher autocomplete; publisher UUIDs are not entered manually in
the GUI. Mapping a filename candidate to an existing publisher or performer does
not create an alias by itself. Alias creation requires a second explicit
confirmation, defaults to No, and duplicate aliases differing only by case are
rejected.

Original movie selection follows the CLI rename rules: no movie is omitted, one
movie is used, the unique earliest dated movie is selected from multiple movies,
and undated movies or earliest-date ties require explicit review. An explicit
movie override must already contain the scene.

Filename parsing, database matching, and canonical preview generation run on a
single background GUI worker thread. Superseded refresh results are ignored, and
the background worker is shut down when the window closes.

Keyboard shortcuts:

```text
Shortcut+S        save without rename
Shortcut+Shift+S  save and rename
Shortcut+Alt+S    save as needs review
Shortcut+Down     next queue item
Shortcut+Up       previous queue item
```

Acceptance testing for this workflow uses temporary SQLite databases and
temporary media directories only. The automated coverage in
`SceneReviewSingleRowAcceptanceTest` builds a fixture with publishers,
publisher aliases, performers, performer aliases, series, dated movies,
compilations, unassigned media, existing review scenes, rename collisions,
unchanged filenames, ambiguous movie membership, and a deterministic rename
failure. To run the connected single-row acceptance coverage:

```bash
./gradlew test --tests service.SceneReviewSingleRowAcceptanceTest --rerun-tasks
```

For manual smoke testing, create or copy a fixture database in an operating
system temporary directory and launch the installed GUI with an explicit
database path:

```bash
build/install/JAVDB/bin/JAVDB gui --database "/tmp/javdb-gui-smoke/javdb.db"
```

Do not omit `--database` during smoke testing unless you intentionally want to
open the configured default database. The current-page READY batch action is
still deferred and is not part of this GUI slice.

## Media Library / Scan Review

The `Media Library` tab shows every stored `media_file`, including assigned
files, so scan results and metadata quality can be inspected without changing
catalog data. The table displays filename, directory, resolution, duration,
readable file size, last-modified time, and an assignment summary of
`Unassigned`, `Scene`, `Movie`, or `Scene + Movie`.

Filters run in SQLite rather than loading the collection into memory. They
cover filename/full-path text, directory prefix, assignment state, exact and
minimum dimensions, missing dimensions, and missing duration. Pages use SQL
limit/offset with the same 25, 50, 100, and 250 row choices as the review
workflow. `Refresh` reloads the current page; successful scans also refresh the
library automatically, even when a dirty review draft keeps the editable queue
refresh pending.

Selecting a row loads its details in the background: UUID, full path, current
filesystem existence, stored size and modification time, dimensions, duration,
content hash, scene and movie assignments with names and UUIDs, parse and match
statuses, best filename interpretation, and parser/matcher warnings. This uses
the same filename preview and assignment logic as indexing. Missing files and
uninterpretable filenames are diagnostic states, not errors that alter data.

The Media Library is strictly read-only. It does not edit, delete, reassign, or
index media, and it does not persist scan history. The READY-page batch action
remains deferred. Its purpose is to validate what scanning actually stored
before using the existing review/indexing workflows.

For manual smoke testing without touching the production database, create a
temporary database outside `data/` and launch:

```bash
build/install/JAVDB/bin/JAVDB gui --database "/tmp/javdb-gui-smoke.db"
```

## Scene Verification And Renaming

Scenes have a persistent verification status:

```text
UNVERIFIED
VERIFIED
NEEDS_REVIEW
```

Databases created with schema version 1 are migrated to the current schema and existing scenes default to `UNVERIFIED`.

Canonical media filenames use this structure, omitting absent optional fields:

```text
(YY.MM.DD) Publisher - Series - SxxExx-or-Code - Original Movie - Scene Title - Performer One, Performer Two.ext
```

The existing file extension is preserved. Performer names use primary performer names in deterministic case-insensitive order. When both season/episode and code are present, season/episode is used and a warning is emitted.

Only the original or earliest associated movie is used in the filename. If a scene appears in multiple movies, JAVDB selects the unique earliest release date. If any associated movie is undated, or multiple movies share the earliest date, preview reports review required. Use `--movie <movie-uuid>` to supply an explicit original-movie override; the override must already contain the scene and does not change movie membership.

Preview and `--dry-run` do not move files or update the database. Rename keeps the file in the same directory, never overwrites an existing destination, and updates only the stored media path. If the file move succeeds but the database update fails, JAVDB attempts to move the file back to the original path and leaves the database record unchanged.

Examples:

```bash
build/install/JAVDB/bin/JAVDB scene verification list --status unverified
build/install/JAVDB/bin/JAVDB scene verification set --scene <scene-uuid> --status verified
build/install/JAVDB/bin/JAVDB media rename-preview --media <media-uuid>
build/install/JAVDB/bin/JAVDB media rename --media <media-uuid> --dry-run
build/install/JAVDB/bin/JAVDB media rename --media <media-uuid>
build/install/JAVDB/bin/JAVDB media rename-preview --media <media-uuid> --movie <original-movie-uuid>
build/install/JAVDB/bin/JAVDB scene rename-media --scene <scene-uuid> --dry-run
```

Renaming physical media can affect external tools, playlists, or file links. Keep a database backup and a filesystem backup of the video collection before bulk rename work.

## CSV Media Import

```text
javdb media import --input <csv-file-or-dash> [--fail-fast]
```

Accepted columns are case-insensitive:

```text
path,file_size,duration,duration_millis,width,height,content_hash
```

`last_modified_millis` is also accepted and maps to the existing SQLite column.

Only `path` is required. The CSV reader supports a header row, quoted fields, escaped quotes, blank optional fields, and paths containing commas.

Example file:

```csv
path,width,height
"/video/a,one.mp4",1920,1080
```

Stdin example:

```bash
printf 'path,width,height\n/video/a.mp4,1920,1080\n' \
  | ./gradlew run --args='--database build/test-cli.db media import --input -'
```

Each valid row is committed independently. Without `--fail-fast`, later rows continue after a failed row. Successful rows remain stored if a later row fails. A failed row does not leave a partial media-file record. The command returns success only when every processed row succeeds.

## Media Discovery

Install FFmpeg so `ffprobe` is available on `PATH`, or pass `--ffprobe <path>`.
Check availability with:

```bash
./gradlew run --args='media probe-check'
```

JAVDB scans recursively, does not follow symbolic-link directories by default,
and supports these extensions out of the box:

```text
.mp4 .mkv .avi .mov .m4v .webm .mpg .mpeg .wmv .flv .ts .m2ts
```

Add extensions without replacing the defaults:

```bash
./gradlew run --args='media scan --root "/Volumes/Archive/Videos" --extension vob'
```

New and changed files are probed with:

```text
ffprobe -v error -select_streams v:0 -show_entries stream=width,height -show_entries format=duration -of default=noprint_wrappers=1:nokey=0 <file>
```

Duration is stored as milliseconds. Paths are normalized to absolute paths,
matching `media add`. A file is unchanged when both stored `file_size` and
`last_modified_millis` match the current filesystem values; unchanged files are
not re-probed or hashed.

Scan examples:

```bash
./gradlew run --args='media scan --root "/Volumes/Archive/Videos"'
./gradlew run --args='media scan --root "/Volumes/Archive/Videos" --dry-run'
./gradlew run --args='media scan --root "/Volumes/Archive/Videos" --hash --output tsv' > scan-results.tsv
```

`--hash` computes SHA-256 content hashes and detects duplicate content. Hashing
is opt-in because full-file hashing is expensive for large videos. Duplicate
content remains represented as separate physical media-file records.

Verification examples:

```bash
./gradlew run --args='media verify'
./gradlew run --args='media verify --refresh'
./gradlew run --args='media verify --refresh --dry-run --output tsv'
```

Missing files are reported but not deleted. `--refresh` re-probes changed files
and updates metadata while preserving media UUIDs and relationships. Successful
scan rows remain committed if a later row fails; use `--fail-fast` to stop after
the first row failure.

## GUI Media Locations

The JavaFX GUI can persist media locations for user-initiated scanning. Open the
workflow from `File > Media Locations...`, `File > Scan All Media Locations`, or
the `Scan Media` toolbar button. Configured locations are loaded when the GUI
starts, but they are not scanned automatically.

A media location is an absolute normalized directory path plus enabled and
recursive settings. `Add Folder` uses a JavaFX directory chooser. Removing a
location removes only the saved configuration: JAVDB does not delete physical
files, media records, scene assignments, or movie assignments.

Use `Scan Selected` to scan one configured location, or `Scan All` to scan all
enabled locations. Disabled locations are skipped. Recursive locations include
nested directories; nonrecursive locations scan only files directly inside the
configured directory. When locations overlap in one Scan All run, paths already
processed by an earlier enabled location are skipped for later locations.

GUI scanning uses the same supported-media rules, changed-file detection,
`ffprobe` probing, and media persistence service as the CLI scanner. New files
are inserted, changed files are updated, and unchanged files are not re-probed.
The progress area shows discovery and processing phases, the current directory,
the current filename, the complete current path, location position during Scan
All, determinate progress after discovery completes, and counts for discovered,
processed, new, updated, unchanged, missing, and failed files. Last scan status
and counts are persisted for each configured location.

Scan completion refreshes the Unassigned Media queue only when it can do so
without discarding a dirty review draft. If the editor has unsaved changes and
scan results changed media records, JAVDB leaves the current draft, selection,
rows, filters, page, and page size in place and shows a nonmodal notification:
`New media scan results are available.` Use `Refresh Media Queue` to apply the
pending results. Choosing Keep Editing leaves the pending notification visible;
choosing Discard Changes and Refresh reloads the queue. A successful save that
already refreshes the queues consumes the pending scan-refresh request. Resetting
draft fields does not silently consume pending scan results.

Statuses include `NEVER_SCANNED`, `RUNNING`, `COMPLETED`,
`COMPLETED_WITH_ERRORS`, `CANCELLED`, `FAILED`, and
`DIRECTORY_UNAVAILABLE`. Final summaries distinguish completed, completed with
errors, cancelled, failed, and unavailable-directory scans and include discovered,
processed, new, updated, unchanged, missing, and failed counts. For Scan All, the
summary includes the number of locations and aggregate counts; overlapping
locations skip paths already processed earlier in the same Scan All run.

Cancelling is cooperative. JAVDB checks for cancellation before discovery, after
discovery, before each file, after metadata probing, after optional hashing, and
between Scan All locations. Completed file records are preserved and no later
file is started after cancellation is observed. The current `ffprobe` runner is
not cancelled mid-process by this workflow; cancellation may wait for the active
`ffprobe` invocation to return before stopping at the next checkpoint. Partial
results are persisted with `CANCELLED` status and accurate counts.

Before large catalog maintenance, create a database backup. The CLI alternatives
remain available with `media scan`, `media verify`, and `media unassigned`.

Temporary visible smoke test:

```bash
SMOKE_ROOT="$(mktemp -d /tmp/javdb-media-smoke.XXXXXX)"
mkdir -p "$SMOKE_ROOT/media/child" "$SMOKE_ROOT/other"
printf 'one' > "$SMOKE_ROOT/media/(25.01.01) Smoke - One.mp4"
printf 'two' > "$SMOKE_ROOT/media/child/(25.01.02) Smoke - Two.mp4"
printf 'three' > "$SMOKE_ROOT/other/(25.01.03) Smoke - Three.mp4"
build/install/JAVDB/bin/JAVDB gui --database "$SMOKE_ROOT/javdb-smoke.db"
```

In the GUI, open Media Locations, add `$SMOKE_ROOT/media`, toggle recursive and
enabled, close and reopen the window to confirm settings persist, run Scan
Selected, confirm the current filename and counts change, edit a review draft,
scan a newly added file, confirm the pending-results notification protects the
dirty draft, then use Refresh Media Queue with both Keep Editing and Discard
Changes paths. Add `$SMOKE_ROOT/other`, run Scan All, confirm enabled locations
are processed and disabled locations are skipped, remove one location, and
confirm physical files and media records remain.

## Unassigned Media Workflow

A media file is unassigned when it appears in neither `scene_media_file` nor
`movie_media_file`. JAVDB treats a physical media file as one catalog item for
this workflow: it will not silently reassign, move, detach, or overwrite an
existing scene/movie assignment. Assignment conflicts return a nonzero status.

List unassigned media:

```bash
build/install/JAVDB/bin/JAVDB media unassigned \
  --directory "/Volumes/Archive/Web" \
  --limit 100

build/install/JAVDB/bin/JAVDB media unassigned \
  --min-width 1920 \
  --output tsv
```

Filters are applied in SQL. `--contains` matches path or filename text
case-insensitively, `--directory` matches normalized absolute path prefixes, and
dimension filters support exact width/height or minimum width/height. The
default page size is 100 and the maximum is 1000.

Inspect assignment:

```bash
build/install/JAVDB/bin/JAVDB media assignment --media <media-uuid>
```

Attach an unassigned file to existing records:

```bash
build/install/JAVDB/bin/JAVDB scene attach-media \
  --scene <scene-uuid> \
  --media <media-uuid>

build/install/JAVDB/bin/JAVDB movie attach-media \
  --movie <movie-uuid> \
  --media <media-uuid>
```

Create one scene from media:

```bash
build/install/JAVDB/bin/JAVDB scene create-from-media \
  --media <media-uuid> \
  --publisher <publisher-uuid> \
  --performer <performer-uuid>
```

If `--title` is blank or omitted, JAVDB derives a provisional title from the
filename only: remove the final extension, replace underscores and separator-dot
runs with spaces, preserve meaningful hyphens and capitalization, collapse
whitespace, and do not infer performers, publishers, dates, codes, seasons, or
episodes.

Batch create scenes with shared metadata:

```bash
build/install/JAVDB/bin/JAVDB scene create-from-media \
  --media <media-uuid> \
  --media <media-uuid> \
  --publisher <publisher-uuid> \
  --dry-run \
  --output tsv
```

CSV manifest batch:

```bash
build/install/JAVDB/bin/JAVDB scene create-from-media-batch \
  --input scenes.csv \
  --dry-run \
  --output tsv
```

Manifest columns:

```text
media_id,title,code,release_date,publisher_id,series_id,season,episode,performer_ids
```

`media_id` is required. `performer_ids` uses semicolon-separated UUIDs inside the
CSV field. Blank titles derive from filenames. Dates use ISO-8601
`yyyy-MM-dd`.

Example manifest:

```csv
media_id,title,code,release_date,publisher_id,series_id,season,episode,performer_ids
11111111-1111-1111-1111-111111111111,,ABC-001,2026-01-15,22222222-2222-2222-2222-222222222222,,,,33333333-3333-3333-3333-333333333333;44444444-4444-4444-4444-444444444444
```

Dry-run validates and derives titles but makes no database changes. Without
`--fail-fast`, later rows continue after a failed row. Successful rows remain
committed if a later batch row fails.

## Filename Indexing

Filename indexing previews and applies catalog metadata from unassigned media
filenames. It never silently creates unknown publishers, series, movies, or
performers, and it never writes ambiguous or unresolved rows automatically.

The structural grammar is:

```text
(YY.MM.DD) [Publisher -] [Series -] [S<number>E<number>-or-Code -] [Movie -] Title - Performer One, Performer Two.ext
```

The parser uses only the filename, removes only the final extension, interprets
two-digit years as `2000 + YY`, and splits major fields only on the exact
separator ` - `. Ordinary hyphens inside names and titles are preserved.
Performer candidates are split on commas. Season/episode fields match
`S<number>E<number>` case-insensitively. Code candidates are conservative
letter-prefixed numeric tokens such as `E1919`.

Database matching uses exact case-insensitive primary-name/title and alias
matches for automatic indexing. Prefix and substring matches are suggestions
that require review. Known publisher-series and publisher-movie relationships
rank otherwise plausible interpretations, and a unique exact series or movie
can infer its publisher. Explicit relationship conflicts prevent READY status.

Match statuses:

```text
READY             exactly one high-confidence interpretation can be written
REVIEW_REQUIRED   one likely interpretation needs human confirmation
AMBIGUOUS         multiple equally plausible interpretations remain
UNRESOLVED        required metadata or performer matches are missing
INVALID_FILENAME  structural parsing failed
```

Preview without writing:

```bash
build/install/JAVDB/bin/JAVDB media parse-preview \
  --all-unassigned \
  --directory "/Volumes/Archive/Incoming" \
  --limit 100

build/install/JAVDB/bin/JAVDB media parse-preview \
  --all-unassigned \
  --limit 100 \
  --output tsv > filename-preview.tsv
```

Automatically create scenes only for READY rows:

```bash
build/install/JAVDB/bin/JAVDB scene auto-index \
  --all-unassigned \
  --limit 100 \
  --dry-run

build/install/JAVDB/bin/JAVDB scene auto-index \
  --all-unassigned \
  --limit 100 \
  --output tsv > verification-report.tsv
```

Every created row is reported as `CREATED_VERIFY`; review the report before
treating the row as fully cataloged. If a matched movie is present, JAVDB
appends the new scene to the end of the movie scene list and includes a warning
because filenames do not contain an explicit movie scene order.

Interactive review:

```bash
build/install/JAVDB/bin/JAVDB scene index-review \
  --all-unassigned \
  --limit 50
```

The initial review workflow shows the best interpretation for each selected
file and requires explicit confirmation before writing READY rows. Creating
missing database entities during review is intentionally explicit; automatic
indexing does not guess or create them.

### Performer Candidate Review

`File > Review Performer Candidates...` provides a read-only aggregation of
performer candidates from structurally valid, currently unassigned filenames.
Candidates are grouped case-insensitively, with occurrence counts and example
filenames. The default view focuses unresolved names, ordered by descending
affected-media count with deterministic name ties; text and minimum-count
filters operate on the loaded list. Exact primary-name and
alias matches are shown separately.

Creating a performer is an explicit confirmed action and uses category
`UNKNOWN`. Mapping a candidate to an existing performer is also explicit and
adds the candidate as an alias only after confirmation; no candidates, aliases,
or scenes are created simply by opening or refreshing the review. Invalid
filenames are excluded conservatively. Refreshing performer candidates leaves a
dirty scene-review draft intact and does not auto-index media. Publisher,
series, movie bootstrapping and READY-page batch indexing remain deferred.

Use normal multi-selection and `Create Selected Performers` to explicitly
create unresolved candidates as `UNKNOWN` performers. Confirmation displays the
selected count; each candidate is rechecked before creation, processed
independently, and reported as created, already resolved, or failed. Batch
creation never creates aliases or indexes media.

### Context Candidate Review

`File > Review Context Candidates...` is a read-only diagnostic view of the
role-neutral context segments before a title in structurally valid, currently
unassigned filenames. It groups text case-insensitively, counts each candidate
at most once per media file, and shows affected-file totals, first/second/third
position evidence, representative filenames, and exact catalog matches.

An exact Publisher, Series, or Movie match is shown as evidence (including the
Publisher for matching Series and Movies). A candidate with more than one exact
match remains explicitly ambiguous. The default view puts unresolved and
multiple-match rows first, then orders by affected files and deterministic
name; text and attention-only filters apply to the already loaded list.

Context position is never a role classification, and prefix or substring
suggestions do not classify a row. Opening, refreshing, filtering, and
selecting this view creates no entities, aliases, scenes, assignments, or index
records.

For an unresolved candidate only, `Create Publisher` opens the existing
Publisher dialog with the candidate prefilled as its proposed primary name; the
user still explicitly saves it and may edit aliases. `Map as Publisher Alias`
uses the existing Publisher autocomplete and requires confirmation before adding
the candidate as an alias. Both actions re-check the exact Publisher/Series/
Movie status immediately before writing, so stale candidates make no change and
are refreshed. Neither action infers a Publisher from position, auto-creates a
catalog record, or indexes media. Series and Movie resolution, plus READY-page
batch indexing, remain deferred.

## Database Backup And Restore

Do not casually copy an active SQLite database file, especially when WAL mode is
enabled. A live database may have committed data in companion `-wal` and `-shm`
files, and copying the three files by hand is easy to get wrong.

JAVDB creates backups with SQLite `VACUUM INTO` through the configured
sqlite-jdbc driver. This asks SQLite to produce a consistent standalone snapshot
of the selected database. The implementation writes to a sibling temporary file,
verifies that temporary database, then moves it into place. The requested
destination appears only after verification succeeds. Existing destinations are
refused unless `--overwrite` is supplied; failed overwrite attempts preserve the
old backup.

Create a backup:

```bash
build/install/JAVDB/bin/JAVDB backup create \
  --destination "/Volumes/Backup/JAVDB/javdb-2026-07-16.db"

build/install/JAVDB/bin/JAVDB backup create \
  --destination "/Volumes/Backup/JAVDB/latest.db" \
  --overwrite \
  --output tsv
```

Verify an existing backup:

```bash
build/install/JAVDB/bin/JAVDB backup verify \
  --input "/Volumes/Backup/JAVDB/javdb-2026-07-16.db" \
  --level full
```

`quick` verification checks file existence, nonempty size, SQLite
`PRAGMA quick_check`, `PRAGMA foreign_key_check`, schema version, and the
required JAVDB core tables. `full` uses `PRAGMA integrity_check` instead of
`quick_check`.

Restore to a separate database file:

```bash
build/install/JAVDB/bin/JAVDB backup restore \
  --input "/Volumes/Backup/JAVDB/javdb-2026-07-16.db" \
  --destination "/private/tmp/javdb-restored.db"
```

Restore verifies the input first, copies the closed standalone backup to a
temporary output file, verifies that restored file, then moves it into place.
This initial restore command refuses to replace the currently selected active
database. To use a restored file, select it later with `--database`, or install
it manually while JAVDB is closed.

Backups contain the SQLite catalog only. They do not back up the physical video
collection; use a separate filesystem backup strategy for media files. For
rotation, create timestamped backup filenames and periodically remove old files
with your normal backup tooling after verifying newer backups.

## Examples

Capture created UUIDs:

```bash
DB=build/example.db
PUBLISHER_ID=$(./gradlew -q run --args="--database $DB publisher add --name 'Example Publisher'")
PERFORMER_ID=$(./gradlew -q run --args="--database $DB performer add --name 'Example Performer' --category ACTOR")
MEDIA_ID=$(./gradlew -q run --args="--database $DB media add --path /video/example.mp4 --width 1920 --height 1080")
SCENE_ID=$(./gradlew -q run --args="--database $DB scene add --title 'Example Scene' --publisher $PUBLISHER_ID --performer $PERFORMER_ID --media $MEDIA_ID")
MOVIE_ID=$(./gradlew -q run --args="--database $DB movie add --title 'Example Movie' --publisher $PUBLISHER_ID --scene $SCENE_ID")
```

List and search:

```bash
./gradlew run --args="--database $DB publisher list"
./gradlew run --args="--database $DB scene search-by-performer --performer $PERFORMER_ID"
./gradlew run --args="--database $DB movie show --id $MOVIE_ID"
```
