package pl.kretdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import pl.kretdb.model.Document;
import pl.kretdb.model.Entry;

@RequiredArgsConstructor
public class DocumentProcessor {

    final private DocumentParser parser;
    @Setter
    private Path root;

    @SneakyThrows
    public List<Entry> processDocument(Document d) {
        Path path = findDocumentFile(d);
        
        return parser.parse(Files.newBufferedReader(path))
            .map(ue -> new Entry(d, ue.getLine(), ue.getAttributes()))
            .collect(Collectors.toList());
    }

    /**
     * Returns non-empty optional with document, when document
     * needs update, i.e. its modification date differs from
     * that from the file.
     */
    public Optional<Document> getUpdatedDocument(Document d) {
        return Optional.of(findDocumentFile(d))
            .map(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toString();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
            .filter(newTime -> !newTime.equals(d.getModification()))
            .map(newTime -> new Document(d.getPath(), d.getName(), newTime));
    }

    private Path findDocumentFile(Document d) {
        Path path = Paths.get(d.getPath(), d.getName());
        path = root.resolve(path);
        if (!Files.isRegularFile(path)) {
            throw new NoSuchElementException();
        }
        return path;
    }
}
