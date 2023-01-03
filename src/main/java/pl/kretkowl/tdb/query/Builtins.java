package pl.kretkowl.tdb.query;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.experimental.UtilityClass;
import static pl.kretkowl.tdb.query.Commons.*;

@UtilityClass
public class Builtins {

    public static String dispatch(String name, List<String> args) {
        switch (name) { // it could be done prettier, enum or
        case "nvl": return nvl(args);
        case "nullif": return nullIf(args);
        case "length": return length(args);
        case "substr": return substr(args);
        default: throw new RuntimeException("unknown function: " + name);
        }
    }

    private static String nvl(List<String> args) {
        return args.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static String nullIf(List<String> args) {
        if (args.size() != 2)
            throw new RuntimeException("nullIf takes exactly 2 arguments");
        return convert2Bool(args.get(0)) ? null : args.get(1);
    }

    private static String length(List<String> args) {
        if (args.size() != 1)
            throw new RuntimeException("nullIf takes exactly 1 argument");
        return ifNotNull(args.get(0), s -> Integer.toString(s.length()));
    }

    private static String substr(List<String> args) {
        if (args.size() == 2) 
            return ifNotNull(args, () -> args.get(0).substring(toInt(args.get(1))));
        else if (args.size() == 3) 
            return ifNotNull(args, () -> args.get(0).substring(toInt(args.get(1)), toInt(args.get(2))));
        throw new RuntimeException("substr takes 2 or 3 arguments");
    }

    private static String ifNotNull(String s, Function<String, String> f) {
        return s == null ? null : f.apply(s);
    }

    private static String ifNotNull(List<String> l, Supplier<String> s) {
        return l.stream().anyMatch(Objects::isNull) ? null : s.get();
    }

    private static int toInt(String v) {
        assert v != null;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            throw new RuntimeException("number expected");
        }
    }
}
