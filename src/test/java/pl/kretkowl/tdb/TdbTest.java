package pl.kretkowl.tdb;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;

import static org.mockito.Mockito.argThat;
import static org.mockito.BDDMockito.given;

import org.junit.Before;
import org.junit.Test;

import pl.kretkowl.tdb.CommandLineOptions.Command;
import pl.kretkowl.tdb.CommandLineOptions.OutputType;
import pl.kretkowl.tdb.model.DB;
import pl.kretkowl.tdb.query.QueryContext;
import pl.kretkowl.tdb.query.QueryParser;

public class TdbTest {

    Tdb tdb;
    DocumentProcessor documentProcessor;
    QueryParser queryParser;
    OutputFormatter outputFormatter;
    StoreManager storeManager;

    @Before
    public void prepareMocks() {
        documentProcessor = mock(DocumentProcessor.class);
        queryParser = mock(QueryParser.class);
        outputFormatter = mock(OutputFormatter.class);
        tdb = new Tdb(documentProcessor, queryParser, outputFormatter);
        storeManager = mock(StoreManager.class);
    }

    @Test
    public void shouldRunInit() {
        try (var sms = mockStatic(StoreManager.class)) {
            sms.when(() -> StoreManager.init(any())).thenReturn(storeManager);

            tdb.run(CommandLineOptions.builder().command(Command.INIT).build());

            sms.verify(() -> StoreManager.init(any()));;
        }
    }

    @Test
    public void shouldRunIndex() {
        try (var sms = mockStatic(StoreManager.class)) {
            sms.when(() -> StoreManager.open(any())).thenReturn(storeManager);
            var db = mock(DB.class);
            given(storeManager.load()).willReturn(db);
            given(storeManager.getRoot()).willReturn(Path.of("src/test/resources").toAbsolutePath());

            tdb.run(CommandLineOptions.builder().command(Command.INDEX).file("src/test/resources/test.md").build());
            System.out.println("after run");

            verify(documentProcessor).processDocument(argThat(d -> d.getName().endsWith("test.md")));
            verify(storeManager).store(any());;
        }
    }

    @Test
    public void shouldRunRebuild() {
        try (var sms = mockStatic(StoreManager.class)) {
            sms.when(() -> StoreManager.open(any())).thenReturn(storeManager);
            given(storeManager.getRoot()).willReturn(Path.of("src/test/resources"));
            var db = mock(DB.class);
            given(storeManager.load()).willReturn(db);

            tdb.run(CommandLineOptions.builder().command(Command.REBUILD).build());

            verify(storeManager).store(any());
        }
    }

    @Test
    public void shouldRunQuery() {
        try (var sms = mockStatic(StoreManager.class)) {
            sms.when(() -> StoreManager.open(any())).thenReturn(storeManager);
            var db = mock(DB.class);
            given(storeManager.load()).willReturn(db);
            var qc = mock(QueryContext.class);
            given(queryParser.parseQuery(db, "abc")).willReturn(qc);

            tdb.run(CommandLineOptions.builder().command(Command.QUERY).query("abc").outputType(OutputType.CSV).build());

            verify(qc).execute();
            verify(outputFormatter).prepareOutput(eq(OutputType.CSV), any());
        }
    }
}
