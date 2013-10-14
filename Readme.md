# Remoting Experiments for Neo4j's Cypher using MessagePack and WebSockets

## Usage

* right now queries are hard coded in clients

````
    ./server.sh [db-dir]
    node ws-client.js
````

### Sample Session

````
    Request
    {"query","start n=({ids}) return n", "params": {"ids" : [1,2]},"stats": true,"result":true}

    Response:
    ["n"]
    [{"id":1,"data":{"name":"foo"}}]
    [{"id":2,"data":{"name":"bar"}}]
    {"time": 0, "rows": 2, "bytes": 100}
````

## Ideas:

Write a Cypher only endpoint for Neo4j that uses a fast transport and serialization across multiple client languages.
Cypher Results are streamed from the server. Transaction support. Multithreaded clients and servers
Client examples in ruby, python, php, c#, c, javascript, java, scala, clojure, erlang
Public installation on Heroku for testing

### Serialization
Nodes, Relationships and Paths are converted into maps and lists for serialization recursively
 
    Node : { id : id, [data : {foo:bar}]}
    Relationship : { id : id, start: id, end: id, type : "FOO",  [data : {foo:bar}]}
    Path {start: node, nodes: [nodes], relationships [relationships], end: node, lenght: 1}

Header with Columns, optional Footer with time, bytes, tx-id, error, exception, rows, update-counts for nodes, relationships, properties.

### Compactness

* leave off footer, enable when needed
* ignore results (fire & forget)

### Transactions

* provide a "tx" parameter with `begin`, `commit`,`rollback`
* `tx-id` will be reported in footer
* provie a `tx-id` parameter with the transaction id    
* transaction will be suspended, resumed per request (if a tx-id is provided) and finished and removed at rollback/commit

## Serialization

* fast, lightweight, portable

### MessagePack

* messagepack-lite (copied sources and fixed some performance issues)
** original source: https://bitbucket.org/sirbrialliance/msgpack-java-lite

## Transport

* fast, lightweight, portable

### WebSockets

#### Java

* uses netty 4

#### Running as Neo4j Kernel Extension

   The cypher server is now packaged as a Neo4j Kernel Extension. So if you build the jar with mvn package and drop the jar in your neo4j environment
   either in /server/plugins or your classpath/build repository for embedded development, the extension will be started and listen on port 5555 by default.
   
   The server starts by default with 1 Thread, the multiple threads don't work yet with transactions (wrong transactions resumed in the wrong thread) but for 
   non-transactional access the single server works fine. Threads and port can be configured in `neo4j.properties` or in the config-map passed to the database.

````
   cypher_remoting_address=:5555 # a hostname and port 
   cypher_remoting_threads=1 # number of threads 1 to 10
````

#### Node.js

* uses "ws" npm module
* simple sample client

## Resources

### Websockets

*
* Netty:
* Node.js

### MessagePack

* http://msgpack.org/
* used msgpack implementation: https://bitbucket.org/sirbrialliance/msgpack-java-lite/overview
* https://github.com/msgpack/msgpack-java/blob/master/src/test/java/org/msgpack/TestSimpleArrays.java
* http://blog.andrewvc.com/why-arent-you-using-messagepack discussion: http://news.ycombinator.com/item?id=2571729

### Alternatives:
* Spread http://www.spread.org/ Spread, a asynchronous messaging protocol
* Netty: https://netty.io/ high performance NIO server/client, only for Java/JVM
* Storm (uses ZeroMQ): http://storm-project.net/ Distributed and fault-tolerant realtime computation: stream processing, continuous computation, distributed RPC
* Thrift: http://thrift.apache.org/ Code generator and RPC middleware for cross-language client-server applications
* BSON: http://bsonspec.org/
* Protocol Buffers http://code.google.com/p/protobuf/
