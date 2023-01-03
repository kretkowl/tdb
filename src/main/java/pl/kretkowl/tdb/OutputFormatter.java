package pl.kretkowl.tdb;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.ToString;

import static java.util.stream.Collectors.toList;

import java.util.LinkedHashMap;

import static java.util.stream.Collectors.joining;

import pl.kretkowl.tdb.CommandLineOptions.OutputType;

public class OutputFormatter {

    public void prepareOutput(OutputType outputType, Stream<Map<String, String>> result) {
        switch (outputType) {
            case SINGLE_VALUE:
                result
                    .findFirst()
                    .flatMap(row -> row.entrySet().stream().findAny())
                    .map(Entry::getValue)
                    .ifPresent(System.out::println);
                break;
            case SINGLE_ROW:
                result
                    .findFirst()
                    .map(row -> row.entrySet().stream())
                    .orElseGet(Stream::empty)
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .forEach(System.out::println);
                break;
            case CSV: {
                var res = result.collect(toList());
                var columns = res.stream()
                    .flatMap(ma -> ma.keySet().stream())
                    .distinct()
                    .collect(toList());
                System.out.println(columns.stream().collect(joining(";")));
                res.stream()
                    .map(ma -> columns.stream().map(ma::get).collect(joining(";")))
                    .forEach(System.out::println);
                break;
            }
            case TABLE: {

                @ToString
                @AllArgsConstructor
                class ColumnData {
                    String name;
                    int length;
                };
                var res = result.collect(toList());
                var columns =
                        res.stream()
                            .flatMap(ma -> ma.entrySet().stream())
                            .map(e -> new ColumnData(
                                        e.getKey(), 
                                        max(4, e.getKey().length(), e.getValue() == null ? 4 : e.getValue().length())))
                            .<Map<String, Integer>>collect(
                                    LinkedHashMap::new, 
                                    (m, cd) -> m.merge(cd.name, cd.length,(v1, v2) -> v1 > v2 ? v1 : v2), 
                                    (m1, m2) -> m2.forEach((k,v) -> m1.merge(k, v, (v1, v2) -> v1 > v2 ? v1 : v2)));
                System.out.println(columns.entrySet().stream().map(cd -> " " + cd.getKey() + " ".repeat(cd.getValue() - cd.getKey().length() + 1)).collect(tableJoin()));
                System.out.println(columns.entrySet().stream().map(cd -> "-".repeat(cd.getValue() + 2)).collect(tableJoin()));
                res.stream()
                    .map(ma -> columns.entrySet().stream()
                        .map(c -> " " + ma.get(c.getKey()) + " ".repeat(c.getValue() - ma.getOrDefault(c.getKey(), "null").length() + 1))
                        .collect(tableJoin()))
                    .forEach(System.out::println);
                break;
            }
        }
    }

    private static Collector<CharSequence, ?, String> tableJoin() {
        return Collectors.joining("|", "|", "|");
    }
    private static int max(int a, int b, int c) {
        return a > b ? (a > c ? a : c) : (b > c ? b : c);
    }
}
