package cli;

final class HelpText {
    private HelpText() {
    }

    static String text() {
        return """
                JAVDB command line

                Usage:
                  javdb help
                  javdb --help
                  javdb init [--database <path>]
                  javdb gui [--database <path>]
                  javdb publisher add --name <name> [--alias <alias>]...
                  javdb publisher list
                  javdb performer add --name <name> --category <category> [--alias <alias>]...
                  javdb performer list
                  javdb series add --title <title> --publisher <publisher-uuid>
                  javdb series list
                  javdb media add --path <file-path> [--file-size <bytes>] [--duration <millis>] [--width <px>] [--height <px>] [--hash <hash>] [--last-modified <millis>]
                  javdb media list
                  javdb media show --id <media-uuid>
                  javdb media import --input <csv-file-or-dash> [--fail-fast]
                  javdb media scan --root <directory> [--ffprobe <path>] [--hash] [--dry-run] [--fail-fast] [--extension <ext>]...
                  javdb media verify [--ffprobe <path>] [--refresh] [--dry-run] [--fail-fast]
                  javdb media probe-check [--ffprobe <path>]
                  javdb media unassigned [--contains <text>] [--directory <path>] [--width <px>] [--height <px>] [--min-width <px>] [--min-height <px>] [--limit <count>] [--offset <count>]
                  javdb media assignment --media <media-uuid>
                  javdb media parse-preview [--media <media-uuid>]... [--all-unassigned] [--contains <text>] [--directory <path>] [--limit <count>] [--offset <count>]
                  javdb media rename-preview --media <media-uuid> [--scene <scene-uuid>] [--movie <movie-uuid>]
                  javdb media rename --media <media-uuid> [--scene <scene-uuid>] [--movie <movie-uuid>] [--dry-run]
                  javdb scene add --title <title> --publisher <publisher-uuid> [--performer <uuid>]...
                  javdb scene list
                  javdb scene show --id <scene-uuid>
                  javdb scene search-by-performer --performer <performer-uuid>
                  javdb scene attach-media --scene <scene-uuid> --media <media-uuid>
                  javdb scene create-from-media --media <media-uuid> --publisher <publisher-uuid> [--title <title>] [--performer <uuid>]...
                  javdb scene create-from-media-batch --input <csv-file-or-dash> [--dry-run] [--fail-fast]
                  javdb scene auto-index [--media <media-uuid>]... [--all-unassigned] [--limit <count>] [--offset <count>] [--dry-run] [--fail-fast]
                  javdb scene index-review [--media <media-uuid>]... [--all-unassigned] [--limit <count>] [--offset <count>]
                  javdb scene verification list [--status unverified|verified|needs-review] [--limit <count>] [--offset <count>]
                  javdb scene verification set --scene <scene-uuid> --status unverified|verified|needs-review
                  javdb scene rename-media --scene <scene-uuid> [--movie <movie-uuid>] [--dry-run] [--fail-fast]
                  javdb movie add --title <title> --publisher <publisher-uuid> [--scene <uuid>]...
                  javdb movie list
                  javdb movie show --id <movie-uuid>
                  javdb movie attach-media --movie <movie-uuid> --media <media-uuid>
                  javdb backup create --destination <backup-path> [--overwrite] [--verify quick|full]
                  javdb backup verify --input <backup-path> [--level quick|full]
                  javdb backup restore --input <backup-path> --destination <new-database-path> [--overwrite] [--level quick|full]

                Global options:
                  --database <path>    Use a database other than data/javdb.db
                  --output tsv         Print tab-separated output where supported
                """;
    }
}
