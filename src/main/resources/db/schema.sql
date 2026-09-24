-- Application metadata
CREATE TABLE IF NOT EXISTS app_metadata (
    metadata_key TEXT PRIMARY KEY,
    metadata_value TEXT NOT NULL
);

-- Performers
CREATE TABLE IF NOT EXISTS performer (
    id TEXT PRIMARY KEY,
    main_name TEXT NOT NULL COLLATE NOCASE,
    category TEXT NOT NULL CHECK (
        category IN ('ACTRESS', 'ACTOR', 'OTHER', 'UNKNOWN')
    )
);

CREATE TABLE IF NOT EXISTS performer_alias (
    performer_id TEXT NOT NULL,
    alias TEXT NOT NULL COLLATE NOCASE,
    PRIMARY KEY (performer_id, alias),
    FOREIGN KEY (performer_id) REFERENCES performer(id) ON DELETE CASCADE
);

-- Publishers and series
CREATE TABLE IF NOT EXISTS publisher (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL COLLATE NOCASE
);

CREATE TABLE IF NOT EXISTS publisher_alias (
    publisher_id TEXT NOT NULL,
    alias TEXT NOT NULL COLLATE NOCASE,
    PRIMARY KEY (publisher_id, alias),
    FOREIGN KEY (publisher_id) REFERENCES publisher(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS series (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL COLLATE NOCASE,
    publisher_id TEXT NOT NULL,
    UNIQUE (publisher_id, title),
    FOREIGN KEY (publisher_id) REFERENCES publisher(id)
);

-- Scenes and movies
CREATE TABLE IF NOT EXISTS scene (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL COLLATE NOCASE,
    code TEXT COLLATE NOCASE,
    release_date TEXT,
    publisher_id TEXT NOT NULL,
    series_id TEXT,
    season TEXT,
    episode TEXT,
    verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED' CHECK (
        verification_status IN ('UNVERIFIED', 'VERIFIED', 'NEEDS_REVIEW')
    ),
    FOREIGN KEY (publisher_id) REFERENCES publisher(id),
    FOREIGN KEY (series_id) REFERENCES series(id)
);

CREATE TABLE IF NOT EXISTS movie (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL COLLATE NOCASE,
    release_date TEXT,
    publisher_id TEXT NOT NULL,
    compilation INTEGER NOT NULL DEFAULT FALSE CHECK (
        compilation IN (FALSE, TRUE)
    ),
    FOREIGN KEY (publisher_id) REFERENCES publisher(id)
);

-- Physical media files
CREATE TABLE IF NOT EXISTS media_file (
    id TEXT PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    file_size INTEGER,
    duration_millis INTEGER,
    width INTEGER,
    height INTEGER,
    content_hash TEXT,
    last_modified_millis INTEGER
);

CREATE TABLE IF NOT EXISTS media_location (
    id TEXT PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    enabled INTEGER NOT NULL CHECK (enabled IN (FALSE, TRUE)),
    recursive INTEGER NOT NULL CHECK (recursive IN (FALSE, TRUE)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_scan_started_at TEXT,
    last_scan_completed_at TEXT,
    last_scan_status TEXT NOT NULL DEFAULT 'NEVER_SCANNED' CHECK (
        last_scan_status IN (
            'NEVER_SCANNED',
            'RUNNING',
            'COMPLETED',
            'COMPLETED_WITH_ERRORS',
            'CANCELLED',
            'FAILED',
            'DIRECTORY_UNAVAILABLE'
        )
    ),
    last_scan_message TEXT,
    last_discovered_count INTEGER NOT NULL DEFAULT 0,
    last_new_count INTEGER NOT NULL DEFAULT 0,
    last_updated_count INTEGER NOT NULL DEFAULT 0,
    last_unchanged_count INTEGER NOT NULL DEFAULT 0,
    last_missing_count INTEGER NOT NULL DEFAULT 0,
    last_failed_count INTEGER NOT NULL DEFAULT 0
);

-- Relationships
CREATE TABLE IF NOT EXISTS scene_performer (
    scene_id TEXT NOT NULL,
    performer_id TEXT NOT NULL,
    PRIMARY KEY (scene_id, performer_id),
    FOREIGN KEY (scene_id) REFERENCES scene(id) ON DELETE CASCADE,
    FOREIGN KEY (performer_id) REFERENCES performer(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movie_scene (
    movie_id TEXT NOT NULL,
    scene_id TEXT NOT NULL,
    scene_order INTEGER NOT NULL,
    PRIMARY KEY (movie_id, scene_id),
    UNIQUE (movie_id, scene_order),
    FOREIGN KEY (movie_id) REFERENCES movie(id) ON DELETE CASCADE,
    FOREIGN KEY (scene_id) REFERENCES scene(id)
);

CREATE TABLE IF NOT EXISTS scene_media_file (
    scene_id TEXT NOT NULL,
    media_file_id TEXT NOT NULL,
    PRIMARY KEY (scene_id, media_file_id),
    FOREIGN KEY (scene_id) REFERENCES scene(id) ON DELETE CASCADE,
    FOREIGN KEY (media_file_id) REFERENCES media_file(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movie_media_file (
    movie_id TEXT NOT NULL,
    media_file_id TEXT NOT NULL,
    PRIMARY KEY (movie_id, media_file_id),
    FOREIGN KEY (movie_id) REFERENCES movie(id) ON DELETE CASCADE,
    FOREIGN KEY (media_file_id) REFERENCES media_file(id) ON DELETE CASCADE
);

-- Search indexes
CREATE INDEX IF NOT EXISTS idx_performer_main_name
    ON performer(main_name);

CREATE INDEX IF NOT EXISTS idx_performer_alias
    ON performer_alias(alias);

CREATE INDEX IF NOT EXISTS idx_publisher_name
    ON publisher(name);

CREATE INDEX IF NOT EXISTS idx_publisher_alias
    ON publisher_alias(alias);

CREATE INDEX IF NOT EXISTS idx_series_title
    ON series(title);

CREATE INDEX IF NOT EXISTS idx_scene_title
    ON scene(title);

CREATE INDEX IF NOT EXISTS idx_scene_code
    ON scene(code);

CREATE INDEX IF NOT EXISTS idx_scene_release_date
    ON scene(release_date);

CREATE INDEX IF NOT EXISTS idx_movie_title
    ON movie(title);

CREATE INDEX IF NOT EXISTS idx_movie_release_date
    ON movie(release_date);

CREATE INDEX IF NOT EXISTS idx_scene_performer_performer
    ON scene_performer(performer_id);

CREATE INDEX IF NOT EXISTS idx_movie_scene_scene
    ON movie_scene(scene_id);

CREATE INDEX IF NOT EXISTS idx_media_file_hash
    ON media_file(content_hash);

CREATE INDEX IF NOT EXISTS idx_media_file_resolution
    ON media_file(width, height);
