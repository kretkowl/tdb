package pl.kretdb.query;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import pl.kretdb.model.DB;
import pl.kretdb.model.Document;
import pl.kretdb.model.Entry;

public class QueryParserTest {

    private static void testAndAssertParseFail(String query) {
        try {
            new QueryParser().parseQuery(new DB(), query);
        } catch (IllegalStateException e) {
            return;
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            throw npe;
        }
        fail();
    }

    @Test
    public void shouldParseSelectAllFromAll() {
        System.out.println("== start all from all ==");
        var query = "from * select *";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldParseSelectAllFromName() {
        System.out.println("== start all from name ==");
        var query = "from name select *";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldParseSelectAllFromNameWithAlias() {
        System.out.println("== start all from name ==");
        var query = "from name n select *";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldParseSelect1Column() {
        System.out.println("== start 1 column from name ==");
        var query = "from name n select a";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldParseSelectColumnList() {
        System.out.println("== start columns from name ==");
        var query = "from name n select a, b";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldParseSelectColumnListWithAlias() {
        System.out.println("== start columns with alias from name ==");
        var query = "from name n select a aa, b bb";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldParseSelectVariousExpressions() {
        System.out.println("== start select expressions ==");
        var query = "from name n select 1, 'abc ''', 1 < 2, fun(a), (a), 1 is not null, a in ('a', 'b')";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldParseWhere() {
        System.out.println("== start where ==");
        var query = "from name n where n.a in ('1', '2') select a, b";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldFailOnInvaldIs() {
        System.out.println("== start invalid is ==");
        var query = "from name 1 select 1 is funny";

        testAndAssertParseFail(query);
    }
    @Test
    public void shouldFailOnUnfinishedOpExpr() {
        System.out.println("== start unfinished op expr ==");
        var query = "from name 1 select 1 <=";

        testAndAssertParseFail(query);
    }

    @Test
    public void shouldFailOnUnfinishedExprList() {
        System.out.println("== start unfinished expr list ==");
        var query = "from name 1 select 1 in (2, )";

        testAndAssertParseFail(query);
    }

    @Test
    public void shouldParseSubquery() {
        System.out.println("== start subquery ==");
        var query = "from (from * select *) select a, b";

        new QueryParser().parseQuery(new DB(), query);
    }

    @Test
    public void shouldFailOnInvalidProjectionList() {
        System.out.println("== start invalid projection list ==");
        var query = "from name 1 select a, b,";

        testAndAssertParseFail(query);
    }

    @Test
    public void shouldFailOnInvalidAlias() {
        System.out.println("== start invalid alias ==");
        var query = "from name 1 select *";

        testAndAssertParseFail(query);
    }

    @Test
    public void shouldFailOnUnexpectedTokenAfterSelect() {
        System.out.println("== start unexpected token ==");
        var query = "from name select * aa";

        testAndAssertParseFail(query);
    }

    @Test
    public void shouldFailOnLackOfFrom() {
        System.out.println("== start lack from ==");
        var query = "select * aa";

        testAndAssertParseFail(query);
    }

    private static DB prepare1EntryDB() {
        DB ret = new DB();
        var doc = new Document("path", "name", null);
        var entry = new Entry(doc, 1, Map.of("a", "A", "bb", "1"));
        ret.add(doc, Collections.singleton(entry));
        return ret;
    }

    private void assertEmptyResult(Stream<Map<String, String>> res) {
        var lst = res.collect(Collectors.toList());
        assertThat("size", lst.size(), is(0));
    }

    private void assertSingleResult(Stream<Map<String, String>> res, String key, String value) {
        var lst = res.collect(Collectors.toList());
        assertThat("size", lst.size(), is(1));
        var el = lst.get(0);
        assertThat("key exists", el.keySet(), hasItem(key));
        assertThat("correct value", el.get(key), equalTo(value));
    }

    @Test
    public void shouldSelectSingleValue() {
        var query = "from * select *";

        assertSingleResult(new QueryParser().parseQuery(prepare1EntryDB(), query).execute(), "a", "A");
    }

    @Test
    public void shouldSelectSingleValueWithAlias() {
        var query = "from name where true select bb c";

        assertSingleResult(new QueryParser().parseQuery(prepare1EntryDB(), query).execute(), "c", "1");
    }

    @Test
    public void shouldSelectCartesian() {
        var query = "from name n1, name n2 where n1.a = n2.a select n1.a";

        assertSingleResult(new QueryParser().parseQuery(prepare1EntryDB(), query).execute(), "a", "A");
    }

    @Test
    public void shouldSelectEmpty() {
        var query = "from name n1 where n1.a in ('x', 'y') select n1.a";

        assertEmptyResult(new QueryParser().parseQuery(prepare1EntryDB(), query).execute());
    }

    @Test 
    public void shouldSelectSubquery() {
        var query = "from (from * select *) n1, name n2 where n1.a = n2.a select n1.a";

        assertSingleResult(new QueryParser().parseQuery(prepare1EntryDB(), query).execute(), "a", "A");
    }

}
