package pl.kretkowl.tdb;

import org.junit.Test;

import pl.kretkowl.tdb.CommandLineOptions.Command;
import pl.kretkowl.tdb.CommandLineOptions.OutputType;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

public class CommandLineParserTest {

    @Test
    public void shouldFailOnUnknownOption() {
        try {
            new CommandLineParser().parseCommandLine(new String[] { "abc", "file.xyz" });
            fail();
        } catch (RuntimeException e) {
            // nop
        }
    }

    @Test
    public void shouldFailOnNoOption() {
        try {
            new CommandLineParser().parseCommandLine(new String[] { });
            fail();
        } catch (RuntimeException e) {
            // nop
        }
    }

    @Test
    public void shouldParseInit() {
        var opt = new CommandLineParser().parseCommandLine(new String[] { "init", "-i" });

        assertThat(opt.getCommand(), equalTo(Command.INIT));
        assertThat(opt.isIndex(), is(true));
    }

    @Test
    public void shouldParseQuery() {
        var opt = new CommandLineParser().parseCommandLine(new String[] { "query", "-v", "-q", "@query" });

        assertThat(opt.getCommand(), equalTo(Command.QUERY));
        assertThat(opt.getOutputType(), equalTo(OutputType.SINGLE_VALUE));
        assertThat(opt.getQuery(), equalTo("@query"));
    }

    @Test
    public void shouldAllowQueryWithoutQ() {
        var opt = new CommandLineParser().parseCommandLine(new String[] { "query", "-v" });

        assertThat(opt.getCommand(), equalTo(Command.QUERY));
        assertThat(opt.getOutputType(), equalTo(OutputType.SINGLE_VALUE));
        assertThat(opt.getQuery(), nullValue());
    }

    @Test
    public void shouldFailOnQueryWithoutQuery() {
        try {
            new CommandLineParser().parseCommandLine(new String[] { "query", "-q" });
            fail();
        } catch (RuntimeException e) {
            // nop
        }
    }

    @Test
    public void shouldParseIndex() {
        var opt = new CommandLineParser().parseCommandLine(new String[] { "index", "file" });

        assertThat(opt.getCommand(), equalTo(Command.INDEX));
        assertThat(opt.getFile(), equalTo("file"));
    }

    @Test
    public void shouldFailOnIndexWithoutFilename() {
        try {
            new CommandLineParser().parseCommandLine(new String[] { "index" });
            fail();
        } catch (RuntimeException e) {
            // nop
        }
    }
}
