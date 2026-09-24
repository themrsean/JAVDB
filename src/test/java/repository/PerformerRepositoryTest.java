package repository;

import database.DatabaseManager;
import database.SchemaManager;
import model.Performer;
import model.PerformerCategory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class PerformerRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "performer-repository-test.db";
    private static final String SELECT_PERFORMER_SQL = """
            SELECT main_name, category
            FROM performer
            WHERE id = ?
            """;
    private static final String SELECT_ALIASES_SQL = """
            SELECT alias
            FROM performer_alias
            WHERE performer_id = ?
            ORDER BY alias COLLATE NOCASE
            """;
    private static final String COUNT_PERFORMERS_SQL = """
            SELECT COUNT(*)
            FROM performer
            """;
    private static final String COUNT_PERFORMER_BY_ID_SQL = """
            SELECT COUNT(*)
            FROM performer
            WHERE id = ?
            """;
    private static final String COUNT_ALIASES_SQL = """
            SELECT COUNT(*)
            FROM performer_alias
            WHERE performer_id = ?
            """;
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int FIRST_COLUMN_INDEX = 1;
    private static final int SECOND_COLUMN_INDEX = 2;

    private static final UUID PERFORMER_ID =
            UUID.fromString("11111111-aaaa-1111-aaaa-111111111111");
    private static final UUID SECOND_PERFORMER_ID =
            UUID.fromString("22222222-aaaa-2222-aaaa-222222222222");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-aaaa-9999-aaaa-999999999999");
    private static final String MAIN_NAME = "Zen Actor";
    private static final String UPDATED_NAME = "Zen Performer";
    private static final String SECOND_NAME = "alpha Actor";
    private static final String FIRST_ALIAS = "Zen";
    private static final String SECOND_ALIAS = "Zed";
    private static final String UPDATED_ALIAS = "ZP";
    private static final String DUPLICATE_ALIAS = "Alias";
    private static final String DUPLICATE_ALIAS_DIFFERENT_CASE =
            "alias";
    private static final String SEARCH_TERM = "zen";

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private PerformerRepository repository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new PerformerRepository(databaseManager);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new PerformerRepository(null)
        );
    }

    @Test
    @DisplayName("Insert stores UUID, main name, and category")
    void insertStoresUuidMainNameAndCategory() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR
        ));

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             SELECT_PERFORMER_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    PERFORMER_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(
                        MAIN_NAME,
                        resultSet.getString(FIRST_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        PerformerCategory.ACTOR.name(),
                        resultSet.getString(SECOND_COLUMN_INDEX)
                );
            }
        }
    }

    @Test
    @DisplayName("Insert stores every alias")
    void insertStoresEveryAlias() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR,
                FIRST_ALIAS,
                SECOND_ALIAS
        ));

        Assertions.assertIterableEquals(
                List.of(SECOND_ALIAS, FIRST_ALIAS),
                findAliases(PERFORMER_ID)
        );
    }

    @Test
    @DisplayName("Find by ID reconstructs performer and aliases")
    void findByIdReconstructsPerformerAndAliases() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTRESS,
                FIRST_ALIAS,
                SECOND_ALIAS
        ));

        final Optional<Performer> found =
                repository.findById(PERFORMER_ID);

        Assertions.assertTrue(found.isPresent());
        assertPerformer(
                found.get(),
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTRESS,
                List.of(SECOND_ALIAS, FIRST_ALIAS)
        );
    }

    @Test
    @DisplayName("Find by ID returns empty for unknown UUID")
    void findByIdReturnsEmptyForUnknownUuid() throws Exception {
        Assertions.assertTrue(
                repository.findById(UNKNOWN_ID).isEmpty()
        );
    }

    @Test
    @DisplayName("Find all returns every performer in deterministic order")
    void findAllReturnsEveryPerformerInDeterministicOrder()
            throws Exception {

        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR
        ));
        repository.insert(createPerformer(
                SECOND_PERFORMER_ID,
                SECOND_NAME,
                PerformerCategory.OTHER
        ));

        Assertions.assertEquals(
                List.of(SECOND_PERFORMER_ID, PERFORMER_ID),
                repository.findAll()
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Name search finds a main-name match")
    void searchByNameFindsMainNameMatch() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR
        ));

        Assertions.assertEquals(
                List.of(PERFORMER_ID),
                repository.searchByName(SEARCH_TERM)
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Name search finds an alias match")
    void searchByNameFindsAliasMatch() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR,
                FIRST_ALIAS
        ));

        Assertions.assertEquals(
                List.of(PERFORMER_ID),
                repository.searchByName(FIRST_ALIAS)
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Name search is case-insensitive")
    void searchByNameIsCaseInsensitive() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR
        ));

        Assertions.assertEquals(
                List.of(PERFORMER_ID),
                repository.searchByName("ZEN")
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Name search does not duplicate multi-way matches")
    void searchByNameDoesNotDuplicateMultiWayMatches()
            throws Exception {

        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR,
                FIRST_ALIAS
        ));

        Assertions.assertEquals(
                List.of(PERFORMER_ID),
                repository.searchByName(SEARCH_TERM)
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Update changes the main name")
    void updateChangesMainName() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR
        ));

        repository.update(createPerformer(
                PERFORMER_ID,
                UPDATED_NAME,
                PerformerCategory.ACTOR
        ));

        Assertions.assertEquals(
                UPDATED_NAME,
                repository.findById(PERFORMER_ID).orElseThrow()
                        .getMainName()
        );
    }

    @Test
    @DisplayName("Update changes the category")
    void updateChangesCategory() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR
        ));

        repository.update(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.UNKNOWN
        ));

        Assertions.assertEquals(
                PerformerCategory.UNKNOWN,
                repository.findById(PERFORMER_ID).orElseThrow()
                        .getCategory()
        );
    }

    @Test
    @DisplayName("Update replaces aliases and removes stale aliases")
    void updateReplacesAliasesAndRemovesStaleAliases()
            throws Exception {

        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR,
                FIRST_ALIAS,
                SECOND_ALIAS
        ));

        repository.update(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR,
                SECOND_ALIAS,
                UPDATED_ALIAS
        ));

        Assertions.assertIterableEquals(
                List.of(SECOND_ALIAS, UPDATED_ALIAS),
                findAliases(PERFORMER_ID)
        );
    }

    @Test
    @DisplayName("Delete removes the performer")
    void deleteRemovesPerformer() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR
        ));

        Assertions.assertTrue(repository.delete(PERFORMER_ID));
        Assertions.assertEquals(
                0,
                countById(COUNT_PERFORMER_BY_ID_SQL, PERFORMER_ID)
        );
    }

    @Test
    @DisplayName("Delete removes associated aliases")
    void deleteRemovesAssociatedAliases() throws Exception {
        repository.insert(createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR,
                FIRST_ALIAS
        ));

        repository.delete(PERFORMER_ID);

        Assertions.assertEquals(
                0,
                countById(COUNT_ALIASES_SQL, PERFORMER_ID)
        );
    }

    @Test
    @DisplayName("Delete reports false for unknown UUID")
    void deleteReportsFalseForUnknownUuid() throws Exception {
        Assertions.assertFalse(repository.delete(UNKNOWN_ID));
    }

    @Test
    @DisplayName("Failed multi-table write rolls back completely")
    void failedMultiTableWriteRollsBackCompletely() {
        final Performer performer = createPerformer(
                PERFORMER_ID,
                MAIN_NAME,
                PerformerCategory.ACTOR,
                DUPLICATE_ALIAS,
                DUPLICATE_ALIAS_DIFFERENT_CASE
        );

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.insert(performer)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, countPerformers()),
                () -> Assertions.assertEquals(
                        0,
                        countById(COUNT_ALIASES_SQL, PERFORMER_ID)
                )
        );
    }

    private Performer createPerformer(
            UUID id,
            String mainName,
            PerformerCategory category,
            String... aliases) {

        return new Performer(id, mainName, List.of(aliases), category);
    }

    private void assertPerformer(
            Performer performer,
            UUID id,
            String mainName,
            PerformerCategory category,
            List<String> aliases) {

        Assertions.assertEquals(id, performer.getId());
        Assertions.assertEquals(mainName, performer.getMainName());
        Assertions.assertEquals(category, performer.getCategory());
        Assertions.assertIterableEquals(
                aliases,
                performer.getAliases()
        );
    }

    private List<String> findAliases(UUID performerId) throws Exception {
        final List<String> aliases = new ArrayList<>();

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_ALIASES_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    performerId.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    aliases.add(resultSet.getString(
                            FIRST_COLUMN_INDEX
                    ));
                }
            }
        }

        return aliases;
    }

    private int countPerformers() throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             COUNT_PERFORMERS_SQL
                     );
             ResultSet resultSet =
                     statement.executeQuery()) {

            if (resultSet.next()) {
                count = resultSet.getInt(FIRST_COLUMN_INDEX);
            }
        }

        return count;
    }

    private int countById(String sql, UUID id) throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    count = resultSet.getInt(FIRST_COLUMN_INDEX);
                }
            }
        }

        return count;
    }
}
