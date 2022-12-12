package pl.kretdb.model;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class DBTest {

    public static DB createDB() {
        DB db = new DB();

        Document d1 = new Document("p1", "d1", null);
        Document d2 = new Document("p2", "d2", null);
        List<Entry> e1 = List.of(
                new Entry(d1, 0, Map.of(
                        "a1", "va1_1",
                        "a2", "va2_2"
                        )),
                new Entry(d1, 3, Map.of(
                        "a1", "va1_1",
                        "a3", "va3_2"
                        )),
                new Entry(d1, 6, Map.of(
                        "a1", "va1_2",
                        "a2", "va2_3"
                        )));

        List<Entry> e2 = List.of(
                new Entry(d2, 0, Map.of(
                        "a1", "va1_2",
                        "a2", "va2_2"
                        )),
                new Entry(d2, 3, Map.of(
                        "a1", "va1_1",
                        "a3", "va3_2"
                        )),
                new Entry(d2, 6, Map.of(
                        "a1", "va1_4",
                        "a2", "va2_4"
                        )));
        db.add(d1, e1);
        db.add(d2, e2);

        return db;
    }

    @Test
    public void shouldRemove() {
        DB db = createDB();

        db.remove(new Document("p1", "d1", null));

        assertTrue(db.documents.size() == 1);
        assertTrue(db.findAll().count() == 3);
    }

    @Test
    public void shouldFind() {
        DB db = createDB();

        assertTrue(db.findAll().count() == 6);
        assertTrue(db.findByDocumentName("d1").count() == 3);
        assertTrue(db.findByAttribute("a3", "va3_2").count() == 2);
        assertTrue(db.findByAttribute("a1", "va1_4").count() == 1);
    }
}
