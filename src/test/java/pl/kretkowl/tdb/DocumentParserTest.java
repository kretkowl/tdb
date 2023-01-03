package pl.kretkowl.tdb;

import java.io.StringReader;
import java.util.stream.Collectors;

import org.junit.Test;
import static org.junit.Assert.*;

public class DocumentParserTest {

    @Test
    public void shouldReturnEntries() {
        var doc = 
            " - a1: v1\n" +
            " - a2: v2\n" +
            "\n" +
            "# Header\n" +
            " - a1: v3\n" +
            " - a3: v4\n" +
            "\n" +
            "# Header\n" +
            "## subHeader\n" +
            " - a1: v5\n" +
            "\n" +
            " - a3: v6\n";
        var ret = new DocumentParser()
            .parse(new StringReader(doc))
            .collect(Collectors.toList());
        System.out.println(ret.size());
        assertTrue(ret.size() == 3);
        assertTrue(ret.get(0).getAttributes().size() == 2);
        assertTrue(ret.get(0).getAttributes().get("a1").equals("v1"));
        assertTrue(ret.get(1).getAttributes().get("a3").equals("v4"));
        assertTrue(ret.get(2).getAttributes().get("a1").equals("v5"));
        assertTrue(ret.get(2).getAttributes().get("a3").equals("v6"));
    }
}
