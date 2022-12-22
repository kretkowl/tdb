package pl.kretdb.query;

import java.util.Map;
import java.util.stream.Stream;

public interface Operator {

    public Stream<Map<String, String>> select(QueryContext ctx);
}
