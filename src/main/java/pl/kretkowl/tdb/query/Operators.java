package pl.kretkowl.tdb.query;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static java.util.stream.Collectors.groupingBy;

import java.util.Collections;

import pl.kretkowl.tdb.model.DB;
import pl.kretkowl.tdb.model.Document;
import pl.kretkowl.tdb.model.Entry;
import static pl.kretkowl.tdb.Util.toMap;

public class Operators {

    private static Operator select(Function<DB, Stream<Entry>> entrySelect) {
        return ctx -> entrySelect.apply(ctx.db).map(Entry::getAttributes);
    }

    public static Operator selectAll() {
        return select(DB::findAll);
    }

    public static Operator selectByDocumentName(String name) {
        return select(db -> db.findByDocumentName(name));
    }

    public static Operator selectByDocument(Document d) {
        return select(db -> db.findByDocument(d));
    }

    public static Operator selectByAttribute(String attribute, String value) {
        return select(db -> db.findByAttribute(attribute, value));
    }

    public static Operator selectByAttribute(String attribute, Pattern value) {
        return select(db -> db.findByAttribute(attribute, value));
    }

    private static Operator transform(int base, Function<Stream<Map<String, String>>, Stream<Map<String, String>>> transformer) {
        return ctx -> transformer.apply(ctx.execute(base));
    }

    public static Operator filter(int base, Predicate<Map<String, String>> predicate) {
        return transform(base, s -> s.filter(predicate));
    }

    public static Operator project(int base, UnaryOperator<Map<String, String>> projection) {
        return transform(base, s -> s.map(projection));
    }

    public static Operator sort(int base, Comparator<Map<String, String>> comparator) {
        return transform(base, s -> s.sorted(comparator));
    }

    public static Operator distinct(int base) {
        return transform(base, Stream::distinct);
    }

    public static Operator groupBy(int base, List<String> attributes, Function<List<Map<String, String>>, Map<String, String>> aggregates) {
        return transform(base, s -> {
            var group = s.collect(groupingBy(attrs -> attributes.stream().collect(toMap(a->a, a-> (String)attrs.get(a)))));
            return group.entrySet().stream()
                .map(e -> {
                    Map<String, String> ret = new HashMap<>(e.getKey());
                    ret.putAll(aggregates.apply(e.getValue()));
                    return ret;
                });
        });
    }

    public static Operator cartesian(int base1, int base2) {
        return ctx -> 
            Stream.concat(ctx.execute(base1), Stream.of(Collections.<String, String>emptyMap())).flatMap(m -> 
                Stream.concat(ctx.execute(base2), Stream.of(Collections.<String, String>emptyMap())).map(m2 -> {
                    Map<String, String> newM = new HashMap<>(m);
                    newM.putAll(m2);
                    return newM;
                })).filter(m -> !m.isEmpty());
    }
}
