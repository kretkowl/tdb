package pl.kretkowl.tdb.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DB implements Serializable {
    Set<Document> documents = new HashSet<>();
    Map<String, Set<Document>> documentsByPath = new HashMap<>();
    Map<String, Set<Document>> documentsByName = new HashMap<>();

    Set<Entry> entries = new HashSet<>();
    Map<String, Map<String, Set<Entry>>> entriesByAttribute = new HashMap<>();
    Map<Document, Set<Entry>> entriesByDocument = new HashMap<>();

    public Optional<Document> findDocument(Document document) {
        return documents.stream().filter(d -> d.equals(document)).findAny();
    }

    public Stream<Document> findAllDocuments() {
        return documents.stream();
    }

    public void remove(Document document) {
        if (!documents.remove(document))
            return;
        var docByPath = documentsByPath.get(document.getPath());
        docByPath.remove(document);
        if (docByPath.isEmpty()) documentsByPath.remove(document.getPath());

        var docByName = documentsByName.get(document.getName());
        docByName.remove(document);
        if (docByName.isEmpty()) documentsByName.remove(document.getName());

        var docEntries = entriesByDocument.remove(document);
        if (docEntries == null)
            return;
        entries.removeAll(docEntries);
        docEntries.forEach(e -> 
            e.getAttributes().forEach((a, v) -> {
                entriesByAttribute.get(a).get(v).remove(e);
                if (entriesByAttribute.get(a).get(v).isEmpty())
                    entriesByAttribute.get(a).remove(v);
                if (entriesByAttribute.get(a).isEmpty())
                    entriesByAttribute.remove(a);
            }));
    }

    public void add(Document document, Collection<Entry> entries) {
        documents.add(document);
        documentsByPath.computeIfAbsent(document.getPath(), __ -> new HashSet<>()).add(document);
        documentsByName.computeIfAbsent(document.getName(), __ -> new HashSet<>()).add(document);

        entries.forEach(e -> {
            if (e.getDocument() != document) throw new IllegalArgumentException();
            addEntry(e);
        });
    }

    private void addEntry(Entry entry) {
        entries.add(entry);
        entriesByDocument.computeIfAbsent(entry.getDocument(), __ -> new HashSet<>()).add(entry);
        entry.getAttributes().forEach((a,v) -> 
            entriesByAttribute.computeIfAbsent(a, __ -> new HashMap<>()).computeIfAbsent(v, __ -> new HashSet<>()).add(entry));
    }

    public Stream<Entry> findByDocument(Document d) {
        return entriesByDocument.getOrDefault(d, Collections.emptySet()).stream();
    }

    public Stream<Entry> findByDocumentName(String name) {
        return documentsByName.getOrDefault(name, Collections.emptySet()).stream().flatMap(this::findByDocument);
    }

    public Stream<Entry> findByAttribute(String attribute, String value) {
        return entriesByAttribute.getOrDefault(attribute, Collections.emptyMap()).getOrDefault(value, Collections.emptySet()).stream();
    }

    public Stream<Entry> findByAttribute(String attribute, Pattern value) {
        return entriesByAttribute.getOrDefault(attribute, Collections.emptyMap()).entrySet().stream()
            .filter(e -> value.matcher(e.getKey()).matches())
            .flatMap(e -> e.getValue().stream());
    }

    public Stream<Entry> findAll() {
        return entries.stream();
    }
}
