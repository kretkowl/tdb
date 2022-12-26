package pl.kretdb;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.joining;

import pl.kretdb.CommandLineOptions.OutputType;

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
            case CSV:
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
    }
}
