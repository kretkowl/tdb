package pl.kretkowl.tdb;

import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Util {

    public static <T, K, U> Collector<T, ?, Map<K, U>> toMap(
        Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends U> valueMapper) {
    @SuppressWarnings("unchecked")
    U none = (U) new Object();
    return Collectors.collectingAndThen(
            Collectors.<T, K, U> toMap(keyMapper,
                    valueMapper.andThen(v -> v == null ? none : v)), map -> {
                map.replaceAll((k, v) -> v == none ? null : v);
                return map;
            });
    }

    public static <T, K, U, M extends Map<K, U>>
    Collector<T, ?, M> toMap(Function<? super T, ? extends K> keyMapper,
                             Function<? super T, ? extends U> valueMapper,
                             BinaryOperator<U> mergeFunction,
                             Supplier<M> mapFactory) {
    @SuppressWarnings("unchecked")
    U none = (U) new Object();
    return Collectors.collectingAndThen(
            Collectors.<T, K, U, M> toMap(keyMapper,
                    valueMapper.andThen(v -> v == null ? none : v),
                    mergeFunction,
                    mapFactory), map -> {
                map.replaceAll((k, v) -> v == none ? null : v);
                return map;
            });
    }
}
