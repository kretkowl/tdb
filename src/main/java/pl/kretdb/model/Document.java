package pl.kretdb.model;

import java.io.Serializable;

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
        //todo
        this.path = "";
        this.name = "";
        this.modification = null;
    }
}
