package pl.kretdb;

import pl.kretdb.CommandLineOptions.Command;
import pl.kretdb.CommandLineOptions.OutputType;

public class CommandLineParser {

    public CommandLineOptions parseCommandLine(String args[]) {
        var clob = CommandLineOptions.builder();

        var command = Command.valueOf(args[0].toUpperCase());
        clob.command(command);
        clob.outputType(OutputType.CSV);
        
        int endIndex = args.length-1;
        if (command == Command.INDEX) {
            if (args.length-1 == 0)
                throw new IllegalArgumentException("filename is required");
            clob.file(args[args.length-1]);
            endIndex--;
        } else if (command == Command.QUERY) {
            if (args.length-1 == 0)
                throw new IllegalArgumentException("query is required");
            clob.query(args[args.length-1]);
            endIndex--;
        }

        for (int i=1; i<=endIndex; i++) {
            if (command == Command.QUERY) {
                if (args[i].equals("-r")) { clob.outputType(OutputType.SINGLE_ROW); continue; }
                else if (args[i].equals("-v")) { clob.outputType(OutputType.SINGLE_VALUE); continue; }
            } if (command == Command.INIT) {
                if (args[i].equals("-i")) { clob.index(true); continue; }
            }
            throw new IllegalArgumentException("unknown option " + args[i]);
        }

        return clob.build();
    }
}
