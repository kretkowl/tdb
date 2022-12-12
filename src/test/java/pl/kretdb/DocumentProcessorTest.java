package pl.kretdb;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

import pl.kretdb.model.Document;

public class DocumentProcessorTest {

    @Test
    public void shouldReadTestData() {
        var read = new DocumentProcessor(new DocumentParser()).processDocument(new Document("./src/test/resources", "test.md", null));

        assertTrue(read.size() == 2);
        assertTrue(read.get(0).getAttributes().get("name").equals("Alice"));
    }

    @Test
    public void shouldDetectChangedTimeStamp() {
        var optDoc = new DocumentProcessor(null).getUpdatedDocument(new Document("./src/test/resources", "test.md", "xxx"));

        assertTrue(optDoc.isPresent());
        assertTrue(optDoc.get().getName().equals("test.md"));
    }

    @Test
    public void shouldDetectSameTimeStamp() throws IOException {
        var optDoc = new DocumentProcessor(null).getUpdatedDocument(
                new Document("./src/test/resources", "test.md", Files.getLastModifiedTime(Paths.get("./src/test/resources", "test.md")).toString()));

        assertTrue(optDoc.isEmpty());
    }
}
