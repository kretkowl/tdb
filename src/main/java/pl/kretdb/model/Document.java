package pl.kretdb.model;

import java.io.Serializable;
import java.nio.file.Paths;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Represents single markdown file.
 */
@AllArgsConstructor
@Value
@EqualsAndHashCode(of = {"path", "name"})
public class Document implements Serializable {

    String path;
    String name;
    String modification; 

    public Document(String pathAndName) {
        var p = Paths.get(pathAndName);
        this.path = p.getParent().toString();
        this.name = p.getFileName().toString();
        this.modification = null;
    }
}
