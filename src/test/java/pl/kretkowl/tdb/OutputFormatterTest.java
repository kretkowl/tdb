package pl.kretkowl.tdb;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static java.util.stream.Collectors.toList;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;

import pl.kretkowl.tdb.CommandLineOptions.OutputType;

public class OutputFormatterTest {

    @Before
    public void generateData() {
        System.err.println("before");
    }

    @Test
    public void shouldGenerateSingleValue() {
        Stream<Map<String, String>> data = Stream.of(
                Map.of("a", "A1"),
                Map.of("a", "A2"));

        synchronized (this) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));
            data = data.map(m -> Map.of("a", m.get("a")));

            new OutputFormatter().prepareOutput(OutputType.SINGLE_VALUE, data);

            assertThat(baos.toString(), is("A1\n"));
        }
    }

    @Test
    public void shouldGenerateSingleRow() {
        Stream<Map<String, String>> data = Stream.of(
                new TreeMap<>(Map.of("a", "A1", "b", "B1")),
                new TreeMap<>(Map.of("a", "A2", "b", "B2")));

        synchronized (this) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));
            new OutputFormatter().prepareOutput(OutputType.SINGLE_ROW, data);

            assertThat(baos.toString(), is("a: A1\nb: B1\n"));
        }
    }

    @Test
    public void shouldGenerateCSV() {
        Stream<Map<String, String>> data = Stream.of(
                new TreeMap<>(Map.of("a", "A1", "b", "B1")),
                new TreeMap<>(Map.of("a", "A2", "b", "B2")));

        synchronized (this) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));
            new OutputFormatter().prepareOutput(OutputType.CSV, data);

            assertThat(baos.toString(), is("a;b\nA1;B1\nA2;B2\n"));
        }
    }

    @Test
    public void shouldGenerateTable() {
        Stream<Map<String, String>> data = Stream.of(
                new TreeMap<>(Map.of("a", "A1", "b", "B1")),
                new TreeMap<>(Map.of("a", "longValue", "long label", "C2")));

        synchronized (this) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));
            new OutputFormatter().prepareOutput(OutputType.TABLE, data);

            assertThat(baos.toString().lines().collect(toList()), hasItems(
                        "| a         | b    | long label |",
                        "|-----------|------|------------|",
                        "| A1        | B1   | null       |",
                        "| longValue | null | C2         |"));
        }
    }
}
