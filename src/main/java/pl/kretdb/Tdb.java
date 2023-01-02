package pl.kretdb;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import pl.kretdb.CommandLineOptions.OutputType;
import pl.kretdb.model.DB;
import pl.kretdb.model.Document;
import pl.kretdb.query.QueryParser;

@AllArgsConstructor
public class Tdb {

    DocumentProcessor documentProcessor;
    QueryParser queryParser;
    OutputFormatter outputFormatter;

    public static void printUsage(String command) {
        String usage = 
            "Usage: " + command + " [COMMAND] ([OPTIONS]...)?\n" +
            "  COMMAND: one of: init, rebuild, index, query\n\n" +
            "  init - creates empty root in current directory; if -i given, indexes all files\n" +
            "  rebuild - reindexes all .md files below nearest root\n" +
            "  index - takes one parameter, filename to index\n" +
            "  query - last parameter is query, when in form @<filename> it will be read from that file\n" +
            "          else taken literally (be sure to use quotes). Query can be preceded by option\n" +
            "          deciding on output: -r is single row, every attribute in seperate <key>: <value> line,\n" +
            "          -v is single value without key, -t markdown table, when not specified csv will be used";
        System.err.println(usage);
    }

    public static void main(String[] args) {
        CommandLineOptions clo = null;
        try {
            clo = new CommandLineParser().parseCommandLine(args);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("not enough parameters");
            printUsage("tdb");
            System.exit(-1);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            printUsage("tdb");
            System.exit(-1);
        }
        try {
            new Tdb(
                    new DocumentProcessor(new DocumentParser()),
                    new QueryParser(),
                    new OutputFormatter())
                .run(clo);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(-2);
        }
    }

    @SneakyThrows
    protected void run(CommandLineOptions clo) {
        StoreManager sm;
        switch (clo.getCommand()) {
        case INIT:
            sm = StoreManager.init(Paths.get("."));
            documentProcessor.setRoot(Paths.get("."));
            if (clo.isIndex()) sm.store(rebuild(sm));
            break;
        case REBUILD:
            sm = StoreManager.open(Paths.get("."));
            documentProcessor.setRoot(sm.getRoot());
            sm.store(rebuild(sm));
            break;
        case INDEX:
            sm = StoreManager.open(Paths.get("."));
            documentProcessor.setRoot(sm.getRoot());
            var db = sm.load();
            indexFile(db, sm.getRoot().relativize(Paths.get(clo.getFile()).toAbsolutePath()), sm.getRoot());
            sm.store(db);
            break;
        case QUERY:
            sm = StoreManager.open(Paths.get("."));
            String query;
            if (clo.getQuery().startsWith("@")) 
                query = Files.readString(Paths.get(clo.getQuery().substring(1)));
            else
                query = clo.getQuery();
            prepareOutput(clo.getOutputType(), query(sm.load(), query));
            break;
        }
    }

    private void prepareOutput(OutputType outputType, Stream<Map<String, String>> result) {
        outputFormatter.prepareOutput(outputType, result);
    }

    private Stream<Map<String, String>> query(DB db, String query) {
        return queryParser.parseQuery(db, query).execute();
    }

    @SneakyThrows
    private void indexFile(DB db, Path path, Path root) {
        var doc = new Document(
                path.getParent() == null ? "." : path.getParent().toString(), 
                path.getFileName().toString(), 
                ((FileTime)Files.getAttribute(root.resolve(path), "lastModifiedTime")).toString());
        var entries = documentProcessor.processDocument(doc);
        db.remove(doc);
        db.add(doc, entries);
    }
    
    protected DB rebuild(StoreManager sm) {
        DB db = new DB();
        var root = sm.getRoot();
        try (var files = Files.find(
                    root, 
                    500, 
                    (name, attrs) -> name.getFileName().toString().endsWith(".md") && attrs.isRegularFile(), 
                    FileVisitOption.FOLLOW_LINKS)) {
            files.forEach(p -> indexFile(db, root.relativize(p), root));
            return db;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
