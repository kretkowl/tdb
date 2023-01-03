package pl.kretkowl.tdb.query;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.junit.Test;

public class BuiltinsTest {

    @Test
    public void nvlShouldReturnFirstNotNullValue() {
        assertThat(Builtins.dispatch("nvl", Arrays.asList(null, null, "A")), is("A"));
    }

    @Test
    public void nvlShouldReturnNullOnNulls() {
        assertThat(Builtins.dispatch("nvl", Arrays.asList(null, null)), is(nullValue()));
    }

    @Test
    public void nullifShouldReturnNull() {
        assertThat(Builtins.dispatch("nullif", Arrays.asList("t", "abc")), is(nullValue()));
    }

    @Test
    public void nullifShouldReturnNotNull() {
        assertThat(Builtins.dispatch("nullif", Arrays.asList("f", "abc")), is("abc"));
    }

    @Test
    public void lengthShouldReturnLength() {
        assertThat(Builtins.dispatch("length", Arrays.asList("abc")), is("3"));
    }

    @Test
    public void lengthShouldReturnNull() {
        assertThat(Builtins.dispatch("length", Arrays.asList((String)null)), is(nullValue()));
    }

    @Test
    public void substrShouldReturnSubstring() {
        assertThat(Builtins.dispatch("substr", Arrays.asList("abc", "1")), is("bc"));
    }

    @Test
    public void substrShouldReturnSubstringToGivenEnd() {
        assertThat(Builtins.dispatch("substr", Arrays.asList("abcd", "1", "3")), is("bc"));
    }

    @Test
    public void substrShouldReturnNull() {
        assertThat(Builtins.dispatch("substr", Arrays.asList(null, "1", "2")), is(nullValue()));
    }
}
