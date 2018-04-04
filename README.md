# <img src="docs/logo.png" align = "right"/> SwayDB [![Gitter Chat][gitter-badge]][gitter-link]

[gitter-badge]: https://badges.gitter.im/Join%20Chat.svg
[gitter-link]: https://gitter.im/SwayDB-chat/Lobby

Type-safe & non-blocking key-value storage library for single/multiple disks and in-memory.

Documentation: http://swaydb.io

## Performance

| Storage  type   | Performance                               
|:---------------:|:------------------------------------------------------
| Persistent      | up to `308,000` writes/sec & up to `316,000` reads/sec                
| In memory       | up to `653,000` writes/sec & up to `628,000` reads/sec                

View detailed benchmark results [here](http://swaydb.io/performance/macbook-pro-mid-2014/memory). 

## Features

- Embeddable, Type-safe, non-blocking
- Multiple disks, In-memory & periodically persistent
- Database storage formats
    - Key-value (`Map[K, V]`)
    - Row (`Set[T]`)
- Lazily fetched values
- Configurable Levels
- Configurable cache size
- Concurrent Leveled Compaction
- Optional Memory-mapped files
- Scala Streams
- Bloom filters
- Fault tolerant

[Read more](http://swaydb.io/).

## Demo
```scala
//Iteration: fetch all key-values withing range 10 to 90, update values and batch write updated key-values
db
.from(10)
.untilKey(_ <= 90)
.map {
  case (key, value) =>
    (key, value + "_updated")
} andThen {
   updatedKeyValues =>
     db.batchPut(updatedKeyValues)
}
```
## Quick start
[Quick start demo](http://swaydb.io/quick-start).

## Examples 
- [Creating Tables](http://swaydb.io/examples/creating-tables)
- [Event-sourcing](http://swaydb.io/examples/event-sourcing)
- [Storing data in chunks](http://swaydb.io/examples/storing-data-in-chunks)

## Related GitHub projects
- [SwayDB.examples](https://github.com/simerplaha/SwayDB.examples) - Examples demonstrating features and APIs.
- [SwayDB.benchmark](https://github.com/simerplaha/SwayDB.benchmark) - Benchmarks for write and read performance.
- [SwayDB.stress](https://github.com/simerplaha/SwayDB.stress) - Stress tests.
- [SwayDB.io](https://github.com/simerplaha/SwayDB.io) - Website code.