# E1

 - name: Alice
 - fruit: Apple

# E2

  This is Bob.

 - name: Bob 
 - fruit: Banana

# E2

  This is Ben.

 - name: Ben 
 - fruit: Banana

```tdb-t
FROM * 
ACCUMULATE COUNT(name) count GROUPING BY fruit
SELECT fruit, count 
```
| fruit  | count |
|--------|-------|
| Grape  | 1     |
| Apple  | 1     |
| Banana | 2     |

-----


```tdb-t
FROM * SELECT  name
```
| name    |
|---------|
| Cecylia |
| Alice   |
| Ben     |
| Bob     |

----


f-"zy2ljV/```kynj:,/^---/-1d _PVn:! ../../../tdb query zno

