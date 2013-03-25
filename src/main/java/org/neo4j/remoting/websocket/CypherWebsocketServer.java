package org.neo4j.remoting.websocket;

import org.neo4j.graphdb.*;
import org.neo4j.kernel.EmbeddedGraphDatabase;

import java.io.File;

import static org.neo4j.helpers.collection.MapUtil.stringMap;

public class CypherWebsocketServer {

    public static void main(final String[] args) throws Throwable {
        final File directory = new File(args[0]);
        boolean newDB=!directory.exists();
        System.out.println("Using database "+directory+" new "+newDB);
        final GraphDatabaseService db = new EmbeddedGraphDatabase(args[0],stringMap("cache_type","soft","cypher_remoting_threads","4"));
        if (newDB) initialize(db);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                db.shutdown();
            }
        });
        Thread.currentThread().join();
    }

    private static void initialize(GraphDatabaseService db) {
        Transaction tx = db.beginTx();
        final Node refNode = db.getReferenceNode();
        refNode.setProperty("name", "Name");
        refNode.setProperty("age", 42);
        refNode.setProperty("married", true);
        refNode.setProperty("kids", new String[]{"foo", "bar"});
        refNode.setProperty("bytes", new byte[]{(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef});

        for (int i=0;i<1000;i++) {
            final Relationship rel = refNode.createRelationshipTo(db.createNode(), DynamicRelationshipType.withName("KNOWS"));
            rel.setProperty("since",1900L);
            rel.setProperty("weight",42D);
        }
        tx.success();
        tx.finish();
    }
}