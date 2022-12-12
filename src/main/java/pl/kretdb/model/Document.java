package pl.kretdb.model;

import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Represents single markdown file.
 */
@Value
@EqualsAndHashCode(of = {"path", "name"})
public class Document implements Serializable {

    String path;
    String name;
    String modification; 
}
