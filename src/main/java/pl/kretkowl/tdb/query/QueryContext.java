package pl.kretkowl.tdb.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import pl.kretkowl.tdb.model.DB;

@RequiredArgsConstructor
public class QueryContext {

    final DB db;
    private List<Operator> partialResults = new ArrayList<>();

    int lastIndex() {
        return partialResults.size()-1;
    }

    void addPartial(Operator operator) {
        partialResults.add(operator);
    }

    Stream<Map<String, String>> execute(int i) {
        return partialResults.get(i).select(this);
    }

    public Stream<Map<String, String>> execute() {
        return execute(lastIndex());
    }
}

