package pl.kretkowl.tdb.query;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Commons {

    public static boolean convert2Bool(String s) {
        return s != null && !"f".equalsIgnoreCase(s);
    }

    public static String convert2String(boolean b) {
        return b ? "t" : "f";
    }

    public static Optional<Long> convert2Num(String s) {
        try {
            return Optional.of(Long.parseLong(s));
        } catch (NumberFormatException nfe) {
            return Optional.empty();
        }
    }

    public static BiFunction<String, String, String> numComparator(BiPredicate<Long, Long> numPredicate) {
        return (s1,s2) -> convert2Num(s1).flatMap(n1 -> convert2Num(s2).map(n2 -> numPredicate.test(n1, n2)))
                            .map(Commons::convert2String)
                            .orElse(null);
    }

    
}
