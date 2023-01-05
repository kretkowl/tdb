package pl.kretkowl.tdb;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;

public class DocumentParser {

    @Value
    public static class UnboundEntry {
        int line;
        Map<String, String> attributes;
    }

    public Stream<UnboundEntry> parse(Reader r) {
        var it = new EntryIterator(r instanceof BufferedReader ? (BufferedReader) r : new BufferedReader(r));

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.DISTINCT), false);
    }

    @RequiredArgsConstructor
    private static class EntryIterator implements Iterator<UnboundEntry> {

        private static Pattern ATTRIBUTE = Pattern.compile("^\\s+- ([a-zA-Z_][-a-zA-Z0-9_]*):");
        final BufferedReader r;
        int currentLine = 0;
        boolean atEnd;
        UnboundEntry current;

        @SneakyThrows
        private void tryRead() {
            if (atEnd || current != null)
                return;
            do {
                boolean eof = false;
                UnboundEntry ue = new UnboundEntry(currentLine, new HashMap<>());
                do {
                    String line = r.readLine();
                    currentLine++;
                    if (line == null) {
                        eof = true;
                        break;
                    } else if (line.startsWith("#")) { // header
                        break;
                    }
                    var m = ATTRIBUTE.matcher(line);
                    if (m.find()) {
                        var attribute = m.group(1).toLowerCase();
                        var value = line.substring(m.end()).trim(); // TODO support for multiline values
                        ue.attributes.put(attribute, value);
                    }
                } while (true);
                if (ue.attributes.isEmpty()) {
                    if (eof) {
                        atEnd = true;
                        current = null;
                        break;
                    }
                } else {
                    current = ue;
                    break;
                }
            } while (true);
        }

        @Override
        public boolean hasNext() {
            if (current != null)
                return true;
            tryRead();
            return !atEnd;
        }

        @Override
        public UnboundEntry next() {
            if (current == null)
                tryRead();
            if (atEnd)
                throw new NoSuchElementException();
            var ret = current;
            current = null;
            return ret;
        }
    }
}
