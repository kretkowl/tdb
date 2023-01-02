package pl.kretdb;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CommandLineOptions {

    Command command;

    boolean index;

    String file;

    String query;

    OutputType outputType;

    public enum OutputType { CSV, SINGLE_ROW, SINGLE_VALUE, TABLE }

    public enum Command { INIT, REBUILD, INDEX, QUERY }
}
