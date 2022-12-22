package pl.kretdb.query;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import pl.kretdb.model.DB;
import pl.kretdb.model.Document;
import pl.kretdb.model.Entry;

import static java.util.stream.Collectors.*;
import static java.util.Comparator.*;

public class OperatorsTest {

    private static QueryContext createContext() {
        DB db = new DB();
        var doc = new Document("path", "name", null);
        var entry = new Entry(doc, 1, Map.of("a", "A", "b", "B"));
        var doc2 = new Document("path2", "name2", null);
        var entry2a = new Entry(doc2, 1, Map.of("a", "A", "c", "C1"));
        var entry2b = new Entry(doc2, 1, Map.of("a", "A2", "c", "C2"));
        db.add(doc, Set.of(entry));
        db.add(doc2, Set.of(entry2a, entry2b));
        return new QueryContext(db);
    }

    @Test
    public void selectAllShouldReturnAllEntries() {
        assertThat(Operators.selectAll().select(createContext()).count(), is(3l));
    }

    @Test
    public void selectByDocNameShouldReturnOnlyDoc() {
        var ret = Operators.selectByDocumentName("name").select(createContext()).collect(toList());

        assertThat(ret.size(), is(1));
    }

    @Test
    public void selectByDocShouldReturnOnlyDoc() {
        var ret = Operators.selectByDocument(new Document("path2", "name2", null)).select(createContext()).collect(toList());

        assertThat(ret.size(), is(2));
    }

    @Test
    public void selectByAttributeShouldReturnOnlyAttrVal() {
        var ret = Operators.selectByAttribute("a", "A").select(createContext()).collect(toList());

        assertThat(ret.size(), is(2));
    }

    @Test
    public void filterShouldRemoveOneRow() {
        var qc = createContext();
        qc.addPartial(Operators.selectAll());
        var ret = Operators.filter(0, ma -> ma.get("a").equals("A")).select(qc).collect(toList());

        assertThat(ret.size(), is(2));
    }

    @Test
    public void projectShouldReturnOnlyProjectedValues() {
        var qc = createContext();
        qc.addPartial(Operators.selectByDocumentName("name"));
        var ret = Operators.project(0, ma -> Map.of("val", "xyz")).select(qc).collect(toList());

        assertThat("size", ret.size(), is(1));
        var el = ret.get(0);
        assertThat("key exists", el.keySet(), hasItem("val"));
        assertThat("correct value", el.get("val"), equalTo("xyz"));
    }

    @Test
    public void orderShouldRearrangeItems() {
        var qc = createContext();
        qc.addPartial(Operators.selectAll());
        var ret = Operators.sort(0, Comparator.<Map<String,String>, String>comparing(m->m.get("c"), nullsLast(naturalOrder()))).select(qc).collect(toList());

        assertThat("size", ret.size(), is(3));
        assertThat(ret.get(0).get("c"), equalTo("C1"));
        assertThat(ret.get(1).get("c"), equalTo("C2"));
        assertThat(ret.get(2).get("c"), nullValue());
    }

    @Test
    public void groupByShouldAllowForCountingInGroups() {
        var qc = createContext();
        qc.addPartial(Operators.selectAll());
        var ret = Operators.groupBy(0, List.of("a"), lma->Map.of("count", Integer.toString(lma.size()))).select(qc).collect(toList());

        assertThat("size", ret.size(), is(2));
        var el = ret.get(0);
        assertThat("key exists", el.get("a"), equalTo("A"));
        assertThat("correct value", el.get("count"), equalTo("2"));
        el = ret.get(1);
        assertThat("key exists", el.get("a"), equalTo("A2"));
        assertThat("correct value", el.get("count"), equalTo("1"));
    }

    @Test
    public void cartesianShouldDoFullJoin() {
        var qc = createContext();
        qc.addPartial(Operators.selectByDocumentName("name"));
        qc.addPartial(Operators.selectByDocumentName("name2"));
        var ret = Operators.cartesian(0, 1).select(qc).collect(toList());

        assertThat("size", ret.size(), is(5));
    }
}
