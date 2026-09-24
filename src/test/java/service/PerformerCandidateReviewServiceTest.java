package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Performer;
import model.PerformerCategory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaFileRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;
import repository.MovieRepository;
import repository.UnassignedMediaPathRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class PerformerCandidateReviewServiceTest {
 @TempDir Path temp; private PerformerCandidateReviewService service; private PerformerRepository performers;
 @BeforeEach void setup() throws Exception { var db=new DatabaseManager(temp.resolve("test.db"));new SchemaManager(db).initialize();var media=new MediaFileRepository(db);performers=new PerformerRepository(db);var catalog=new CatalogService(new PublisherRepository(db),performers,new SeriesRepository(db),media,new SceneRepository(db),new SearchRepository(db),new MovieRepository(db));service=new PerformerCandidateReviewService(new UnassignedMediaPathRepository(db),new MediaFilenameParser(),new EntitySuggestionRepository(db),new EntityManagementService(catalog,new PublisherRepository(db),performers));
  add(media,"(25.01.01) Title - Alice, Bob.mp4");add(media,"(25.01.02) Other - alice.mp4");add(media,"bad filename.mp4"); }
 @Test void aggregatesValidCandidatesAndResolvesPrimaryAlias() throws Exception { performers.insert(new Performer(UUID.randomUUID(),"Alice Prime",List.of("Alice"),PerformerCategory.UNKNOWN)); var rows=service.loadCandidates(); var alice=rows.stream().filter(x->x.text().equals("Alice")).findFirst().orElseThrow();var bob=rows.stream().filter(x->x.text().equals("Bob")).findFirst().orElseThrow(); Assertions.assertAll(()->Assertions.assertEquals(2,rows.size()),()->Assertions.assertEquals(2,alice.mediaCount()),()->Assertions.assertEquals(PerformerCandidateResolution.ALIAS_MATCH,alice.resolution()),()->Assertions.assertEquals(PerformerCandidateResolution.UNRESOLVED,bob.resolution())); }
 @Test void explicitCreateAndAliasMappingRefreshResolutionAndRejectDuplicates() throws Exception { var created=service.createPerformer("Bob"); Assertions.assertEquals(PerformerCandidateResolution.PRIMARY_MATCH,service.loadCandidates().stream().filter(x->x.text().equals("Bob")).findFirst().orElseThrow().resolution()); service.mapAlias("Alice",created.getId()); Assertions.assertThrows(IllegalArgumentException.class,()->service.mapAlias("alice",created.getId())); }
 @Test void batchCreatesIndependentlyAndSkipsResolvedCandidates() throws Exception { service.createPerformer("Alice");var batch=service.createPerformers(List.of("Alice","Bob"));Assertions.assertAll(()->Assertions.assertEquals(2,batch.selected()),()->Assertions.assertEquals(1,batch.created()),()->Assertions.assertEquals(1,batch.skipped()),()->Assertions.assertTrue(batch.failures().isEmpty()),()->Assertions.assertEquals(2,performers.findAll().size())); }
 private void add(MediaFileRepository repo,String name)throws Exception{repo.insert(new MediaFile(UUID.randomUUID(),temp.resolve(name),1,null,Duration.ofSeconds(1),1,1,1));}
}
