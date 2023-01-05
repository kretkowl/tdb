TDB
===

Database backed by Markdown files. Allows to execute arbitrary queries over data collected from Markdown files
contained in directory tree. Inspired by dataview's idea, although I've never used it, only read about it.
Runs locally, stores indexed data on disks to speed up queries.

Tools envisioned usage is workflow were modified files are indexed on save, other files contain embedded queries
that are executed on file open and results are pasted below (with editor hiding query source), directly or after
processing/formatting by external tool (to generate better looking tables/charts etc.).

# Document format

In analysed document each header (#) separates a section (entry). In section, all lines in format:

```
   - identifier: value
```

are interpreted as key-value pair (identifier is case-insensitive). Dash (-) must be preceded by one or
more whitespace, identifier has to begin with letter or underscore followed by one or more of letters,
numbers, dashes, underscores. There must be exactly one space between dash and identifier and no space
between identifier and colon. Value is as the tail string till end of line - no multiline values for now.

Parses for Markdown is extremely stupid - it does not care about code blocks etc. 

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
query: --> 'FROM' -> source -+-> 'WHERE' -> expression ---+
                  ^-- ',' ---+                            |  
                                                          |
       +--------------------------------------------------+
       |
       +--+-> accumulate clause ------+
          +-----------------------^   |          
                                      | 
       +------------------------------+
       |
       +-> 'SELECT' -+---------------------> projection +-+
                     +-> 'DISTINCT' -^    ^------ , ----+ |
                                                          |
       +--------------------------------------------------+
       |
       +-+-----------------------------------> end
         +--> 'ORDER BY' -> order clauses -^

order clauses: --> expression --+-->
                ^---- ',' <-----+
```

_Sources_ are full outer joined, i.e. it's cartesian join for each _source_ + null row.
Order is as follows: nulls, then values that can be parsed as nunmber sorted ascending,
then other string sorted lexically.

```
           +-> document location ----+
           +-> '(' -> query -> ')' --v   +---------v
source:  --+-----> document name --------+-> alias -> 
           +---------> '*' -------^
```

_Document location_ is document path (relative to documents root) preceded by `/` and followed by name, 
e.g. `/journal/january` - this matches exact document. _Document name_ `january` would match all documents 
with that name in any directory from root. Parentheses surround _query_ to form a subquery. Star matches 
all entries. _Alias_ is optional and allows referencing by `alias.attribute`, although `attribute` 
still works (but may be shadowed by following sources).

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
Value is true iff it is not null and is not equal `'f'`, `true` / `false` are syntactic sugar.
_Symbol_ is any java identifier that is not keyword (this definition is not strict, it varies in 
different places, but above is generally a good rule of thumb...) + complex symbols 
like `t1.attribute`. 

Functions: _nvl(args..)_ - first not null value, _nullif(t, v)_ if _t_ is truthy - `null`, else _v_,
_length(s)_ - length of string s or `null`, _substr(s, start[, end])_ - substring from start
(including) to end (excluding).

_Operators_: =, !=, <, <=, >, >=, ~ (match reqexp), ||, AND, OR. Comparisons
convert expressions to Java `long`.

```

accumulate clause: --> 'ACCUMULATE' ---> aggregate -+---+
                                     ^----- ',' ----+   |
                                                        |
                    +-----------------------------------+
                    |                                   v
                    +-> 'GROUPING' -> 'BY' --> symbol -+--->
                                            ^--- ',' --+

             +-> 'SUM' ---+
             +-> 'MIN' ---+
             +-> 'MAX' ---v
aggregate: --+-> 'COUNT' --> '(' -> expression -> ')' -> alias ->
```

_Accumulate clause_ allows specifing of one or more aggregates defined as aggragete function call.
`COUNT` returns string representation of number of different values for given expression in group
row set. `SUM` treats expression values as `long` (0 if not parsable). `MIN` and `MAX` sort row set like
_ORDER_ clause and take first/last row respectively. Aggregate requires an alias.

_GROUPING BY_ clause is optional, if omitted, whole partial result becomes group row set and only
aggregates are allowed in _SELECT_ projection. If present, it is followed by list of symbols
that denote attributes in rows (you cannot group by expressions). Care should be taken, as those
symbols become aliases. That is, if you write `GROUP BY t.attr`, you can only use `t.attr` in 
projection expressions and not `attr` alone.

```
projection: --> expression -+-> alias -->
                            +----------^
```

_Aliases_ are optional simple symbols. If not present and cannot be inferred from attribute name (e.g. because
it is complex expression), default `__data_N` is assumed, where N is consecutive natural number. As with
attribute keys, define aliases starting with `__` at your own risk.

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

```
from (
  from tasks t, /work/contacts c
  where t.assignee = c.name 
  accumulate count(t.project) projectCnt grouping by c.name
  select projectCnt, c.name name
)
where projectCnt > 1
select *
```

Selects all employees that work on more than one project along with number of projects they are working on.

## Limitations

Currently queries are compiled and executed without optimization. Optimization can be done 'by hand' to
some degree by choosing source order in from and using subqueries before joining to limit number of
rows processed by where.

# Running

**WARNING** This tool uses standard Java deserialization when reading its indices. As this mechanism can be 
compromised, caution should be taken to not to run it on elevated priviliges, because in highly unlikely 
scenario of preparing and substituting index file, arbitrary code may be executed. This risk does not
exist when using native binaries, because of requirement of all serialized classess specification at 
compile time.

Use `tdb` alone to show usage. Basically, you will run `tdb init -i` in root of your documents (it will create and 
populate db file). Afterwards, you may issue queries with `tdb query (-c|-r|-v|-t) QUERY` (don't forget to escape it), 
optionally saving query to a file and using `@queryFile` syntax. After modifying document, issue `tdb index FILE` it.

You may consider placing a hook on file saving to index it (e.g. using autocommands in vim). Also, as
described in the opening paragraphs, consider parsing files to find embedded queries and execute them
on the fly.

# Building

Application is written in Java, any recent version will do. It has no external dependencies besides
lombok. On my fairy slow machine, execution times are around 0.1s-0.2s when working with small databases with
OpenJDK. To achieve better performance application can be easily native-compiled using GraalVM. Config for
serialization is added in `src/main/resources`, building native image then is as simple as invoking in `target`
directory (after `mvn package`):

    native-image tdb-0.1.0.jar tdb

After native compilation, running simplest query is 10x faster (0.02-0.03s).

Precompiled binaries for x64 Linux are available on github. 

# FAQ

1. Why not use embedded DBMS like H2 or sqlite?

First reason: to minimize dependencies - I had no prior experience with GraalVM, so I feared that using external
libraries which in turn will use reflection/jni/whatever will cause troubles during native image export.

Second: it seemed overkill to use full-fledged DB just for indexing bunch of files. After implementing all of this
I am not so sure about it.

Third: I like writing parsers by hand!

2. Why not use ANTLR or another parser generator?

It wouldn't save much work in short run, as it would complicate generating expressions. In long run, after
considering potential optimization, I'm not so sure anymore that it was a good decision.

3. What are practical applications?

My personal projects have bunch of markdown files, that specify 'backlog' for things I'm planning to do,
history of things I've done and things I'm currently working on. This tool helps me search, count, group
tasks and relieves me from manually taking care of indexing and/or writing them in some order - I can have
one or more 'bags' of tasks.

I also execute queries automatically on opening file and place results inline. This way, I have index files
that are updated when I open them.

4. Can you prepare binaries for Windows/ARM Linux/Mac?

I do not have neither Windows nor ARM Linux box. Use jar.

# License

The GPLv2 License (GPLv2)

Copyright (c) 2023 kretkowl () gmail.com

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

# Planned features

- rebuild that respects modification time
- order by ... desc
- pseudo-columns `__document`, `__line`
- arithmetic operators
- query optimization (joins and select order)
- views
- running without index file

