MAVEN_OPTS="-Xmx256M -Xms256M -server -d64" mvn compile exec:java -Dexec.mainClass=org.neo4j.remoting.websocket.CypherWebsocketServer -Dexec.args=${1-graph.db}
