package pl.kretkowl.tdb.model;

import java.io.Serializable;
import java.util.Map;

import lombok.Value;

/**
 * Single data entry from document.
 */
@Value
public class Entry implements Serializable {

    Document document;
    
    /**
     * Beginning position.
     */
    int line;

    Map<String, String> attributes;
}
