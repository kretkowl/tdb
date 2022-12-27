KreTextDB
=========

Database backed by Markdown files. Allows to execute arbitrary queries over data collected from Markdown files
contained in directory tree. Inspired by dataview's idea, although I've never used it, only read about it.
Runs locally, stores indexed data on disks to speed up queries.

Tools envisioned usage is workflow were modified files are indexed on save, other files contain embedded queries
that are executed on file open and results are pasted below (with editor hiding query source), directly or after
processing/formatting by external tool (to generate better looking tables/charts etc.).

# Document format

In analysed document each header (#) separates a section (entry). In section, all lines in format:

   - identifier: value

are interpreted as key-value pair. Dash (-) must be preceded by one or more whitespace, identifier
has to begin with letter or underscore followed by one or more of letters, numbers, dashes, underscores.
There must be exactly one space between dash and identifier and no space between identifier and colon.
Value is as the tail string till end of line - no multiline values for now.

## Example

Following document:

```md
# Entry 1
 - name: John Doe
 - fruit: apple
 - color: black

# Entry 2
 - name: Alice Gold
 - fruit: watermelon
 - animal: dog
```

contains 2 sections, one with keys name, fruit and color, other with name, fruit and animal. 

# Database

Database is a set of entries mapped from document sections: entry is map (of key-values retrieved as described 
above) and document. Document is represented as path and name with modififcation timestamp. Data is indexed 
with path, document name and attributes.

Entries are not required to have any common structure - even in single document. So it is perfectly valid
to have two entries one after another that have completely different attribute sets.

Although it is not enforced in any way, attribute keys should not start with double underscore. Currently,
queries generate aliases for complex expressions that use this format, but in future there may appear some
other usages.

# Queries

Query langauge is hacked ad hoc with some shortcuts in few hours. You were warned.

```
                      +-------------------------v
--> 'FROM' -> source -+-> 'WHERE' -> expression -> 'SELECT' -> projection +-> end
           ^-- ',' ---+                                     ^------ , ----+
```

_Sources_ are full outer joined, i.e. it's cartesian join for each _source_ + null row.

```
           +-> document location --v +---------v
source:  --+-----> document name ----+-> alias -> 
           +---------> '*' -------^
```

_Document location_ is document path (relative to documents root) preceded by '/' and followed by name, 
e.g. `/journal/january` - this matches exact document. _Document name_ `january` would match all documents 
with that name in any directory from root. Star matches all entries. _Alias_ is optional and allows
referencing by `alias.attribute`, although `attribute` still works (but may be shadowed by following
sources).

```
expression: --+-> 'FALSE' ----------------------------------->
              +-> 'TRUE' -----------------------------------^
              +-> 'NULL' -----------------------------------+
              +-> '(' -> expression -> ')' -----------------+
              +-> number -----------------------------------+
              +-> string -----------------------------------+
              +-> symbol -----------------------------------+
              +-> 'NOT' -> expression ----------------------+
              +-> function -> '(' -> args -> ')' -----------+
              +-> expression -> 'IN' -> '(' -> args -> ')' -+
              +-> expression -> 'IS' -+-----------> 'NULL' -+
              |                       +-> 'NOT' -^          |
              +-> expression -> operator -> expression -----+

args: --> expression -+->
       ^----- ',' ----+
```

_Strings_ are `'` delimited. _Numbers_ are syntactic sugar - they are treated as strings internally.
Value is true iff it is not null and is not equal `'f'`, _true_ / _false_ are syntactic sugar.
_Symbol_ is any java identifier that is not keyword (this definition is not strict, it varies in 
different places, but above is generally a good rule of thumb...) + complex symbols 
like `t1.attribute`. _Operators_: =, !=, <, <=, >, >=, ~ (match reqexp), ||, AND, OR. Comparisons
convert expressions to Java `long`.

```
projection: --> expression -+-> alias -->
                            +----------^
```

_Aliases_ are optional simple symbols. If not present and cannot be inferred from attribute name (e.g. because
it is complex expression), default `__data_N` is assumed, where N is consecutive natural number. As with
attribute keys, defina aliases starting with `__` at your own risk.

Queries are case-insensitive.

Order of attributes in output is the same as expressions in main level projection, undefined if star `*` is used.

## Examples

Let's assume following directory structure (from db root)
 - root
   - work
     - tasks.md
     - contacts.md
   - personal
     - journal.md
     - contacts.md

```
from * select *
```

Selects all attributes from all entries across all documents.

```
from contacts c
where c.name ~ 'Smith'
select name, phone
```

Selects name and phone from entries in both work/contacts.md and personal/contacts.md only if they have
attribute name that matches regexp 'Smith' (because it does not have ^ or $ anchors, it will try to find
'Smith' at any position in name).

```
from tasks t, /work/contacts c
where (t.priority >= 3 or t.project in ('p1', 'p2'))
and t.assignee = c.name 
select c.summary, c.name || ' (' || c.phone || ')' contact
```

Selects tasks with priority greater or equal 3 and all tasks from projects p1 and p2, matches assignee
with contact data from work contacts and outputs task summary with name and phone of person responsible.

## Limitations

Currently queries are compiled and executed without optimization. DB has working sorting and 
grouping, but there is no syntax for it.

# Running

**WARNING** This tool uses standard Java deserialization when reading its indices. As this mechanism can be 
compromised, caution should be taken to not to run it on elevated priviliges, because in highly unlikely 
scenario of preparing and substituting index file, arbitrary code may be executed.

Use `tdb` alone to show usage. Basically, you will run `tdb init -i` in root of your documents (it will create and 
populate db file). Afterwards, you may issue queries with `tdb query (-c|-r|-v) QUERY` (don't forget to escape it), 
optionally saving query to a file and using `@queryFile` syntax. After modifying document, issue `tdb index FILE` it.

You may consider placing a hook on file saving to index it (e.g. using autocommands in vim). Also, as
described in the opening paragraphs, consider parsing files to find embedded queries and execute them
on the fly.

# Building

Application is written in Java, any fairy recent version will do. It has no external dependencies besides
lombok. On my fairy slow machine, execution times are around 0.1s-0.2s when working with small databases with
OpenJDK. To achieve better performance application can be easily native-compiled using GraalVM. Config for
serialization is added in `src/main/resources`, building native image then is as simple as invoking in `target`
directory (after `mvn package`):

    native-image kretdb-1.0-SNAPSHOT.jar tdb

After native compilation, running simplest query is 10x faster (0.02-0.03s).

Precompiled binaries for Linux are available on github. 

# Planned features

- subqueries in `from` (will allow some manual optimization and make some more complex queries possible)
- `group` and `order by` clauses
- functions (nvl, substr, find)
- pseudo-columns `__document`, `__line`
- query optimization (joins and select order)

