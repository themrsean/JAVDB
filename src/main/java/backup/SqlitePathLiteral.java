package backup;

final class SqlitePathLiteral {
    private SqlitePathLiteral() {
    }

    static String quote(java.nio.file.Path path) {
        return "'" + path.toString().replace("'", "''") + "'";
    }
}
