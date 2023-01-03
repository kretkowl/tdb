package pl.kretkowl.tdb;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pl.kretkowl.tdb.model.DB;

@AllArgsConstructor
public class StoreManager {

    public static final Path DB_FILE = Paths.get(".tdb");

    @Getter
    private Path root;

    public static Path findRoot(Path start) {
        start = start.toAbsolutePath();
        do {
            if (Files.exists(start.resolve(DB_FILE)))
                return start;
            start = start.getParent();
        } while (start != null);
        throw new RuntimeException(start + " has no tdb root");
    }

    private Path getDbFilePath() {
        return root.resolve(DB_FILE);
    }

    public DB load() {
        try (var is = new ObjectInputStream(Files.newInputStream(getDbFilePath()))) {
            return (DB) is.readObject();
        } catch (Exception e) {
            throw new RuntimeException("error loading " + getDbFilePath(), e);
        }
    }

    public void store(DB db) {
        try (var os = new ObjectOutputStream(Files.newOutputStream(getDbFilePath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            os.writeObject(db);
        } catch (Exception e) {
            throw new RuntimeException("error saving " + getDbFilePath(), e);
        }
    }

    public static StoreManager open(Path start) {
        return new StoreManager(findRoot(start));
    }

    public static StoreManager init(Path root) {
        var sm = new StoreManager(root.toAbsolutePath());
        sm.store(new DB());
        return sm;
    }
}
