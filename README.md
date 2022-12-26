KreTextDB
=========

Database backed by Markdown files. Allows to execute arbitrary queries over data collected from Markdown files
contained in directory tree. Inspired by dataview's idea, although I've never used it, only read about it.
Runs locally, can store indexed data on disks to speed up queries.

# Document format

In analysed document each header (#) separates a section. In section, all lines in format:

   - identifier: value

are interpreted as key-value pair. Dash (-) must be preceded by one or more whitespace, identifier
has to begin with letter or underscore followed by one or more of letters, numbers, dashes, underscores.
There must be exactly one space between dash and identifier and no space between identifier and colon.
Value is as the tail string till end of line - no multiline values for now.

# Database

Database is a set of entries: entry is map (of key-values retrieved as described above) and document.
Document is represented as path and name with modififcation timestamp. Data is indexed with path, document 
name and attributes.

# Queries

Query langauge is hacked ad hoc with some shortcuts in few hours. You were warned.

                      +-------------------------v
--> 'FROM' -> source -+-> 'WHERE' -> expression -> 'SELECT' -> projection +-> end
           ^-- ',' ---+                                     ^------ , ----+

_Sources_ are full outer joined, i.e. it's cartesian join for each _source_ + null row.

           +-> document location -v  +---------v
source:  --+-----> document name ----+-> alias -> 
           +---------> '*' -------^

_Document location_ is document path (relative to documents root) preceded by '/' and followe by name, 
e.g. `/journal/january`. _Document name_ would be here `january` and would match all documents with
that name in any directory from root. Star matches all entries. _Alias_ is optional and allows
referencing by `alias.attribute`, although `attribute` still works (but may be shadowed by following
sources).

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

_Strings_ are `'` delimited. _Numbers_ are syntactic sugar - they are treated as strings internally.
Value is true iff it is not null and is not equal `'f'`, _true_ / _false_ are synntactic sugar.
_Symbol_ is any java identifier that is not keyword (this definition may vary...) + complex symbols
like `t1.attribute`. _Operators_: =, !=, <, <=, >, >=, ~ (match reqexp), ||, AND, OR. Comparisons
convert expressions to long.

projection: --> expression -+-> alias -->
                            +----------^

_Aliases_ are optional simple symbols. If not present and cannot be inferred, default `__data_N` is assumed, 
where N is consecutive natural number.

Queries are case-inensetive.

Currently queries are compiled and executed without optimization. DB has working sorting and 
grouping, but there is no syntax for it.

# Running

**WARNING** This tool uses standard Java deserialization when reading its indices. As this mechanism can be compromised, caution should be taken
to not to run it on elevated priviliges, because in highly unlikely scenario of preparing and substituting index file, arbitrary code may be 
executed.

TODO


# Building

Application is written in Java, any fairy recent version will do. It has no external dependencies besides
lombok. To achieve performance suitable to running this as a hook on file save/load, application should
be compiled using GraalVM.
