package org.neo4j.remoting.websocket;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.Description;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.extension.KernelExtensionFactory;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.kernel.lifecycle.Lifecycle;

import static org.neo4j.helpers.Settings.*;

/**
 * @author mh
 * @since 06.02.13
 */
public class CypherRemotingKernelExtensionFactory extends KernelExtensionFactory<CypherRemotingKernelExtensionFactory.Dependencies> {

    public static final String SERVICE_NAME = "CYPHER_REMOTING";

    @Description("Settings for the Cypher Remoting Extension")
    public static abstract class CypherRemotingSettings {
        public static GraphDatabaseSetting.HostnamePortSetting cypher_remoting_address = new GraphDatabaseSetting.HostnamePortSetting(setting("cypher_remoting_address", HOSTNAME_PORT, ":5555" )); // todo illegalmessage
        public static final int BUFFER_SIZE = 1 << 15;
        public static GraphDatabaseSetting.IntegerSetting buffer_size = new GraphDatabaseSetting.IntegerSetting(setting("buffer_size", INTEGER, ""+BUFFER_SIZE ));
        public static GraphDatabaseSetting.IntegerSetting cypher_remoting_threads = new GraphDatabaseSetting.IntegerSetting(setting("cypher_remoting_threads", INTEGER, "1", illegalValueMessage("must be a thread number between 1 and 10",range(1,10))));
    }

    public CypherRemotingKernelExtensionFactory() {
        super(SERVICE_NAME);
    }

    @Override
    public Lifecycle newKernelExtension(Dependencies dependencies) throws Throwable {
        Config config = dependencies.getConfig();
        return new CypherWebsocketExtension(dependencies.getGraphDatabaseService(),dependencies.getStringLogger(), config.get(CypherRemotingSettings.cypher_remoting_address),config.get(CypherRemotingSettings.buffer_size),config.get(CypherRemotingSettings.cypher_remoting_threads));
    }

    public interface Dependencies {
        GraphDatabaseService getGraphDatabaseService();
        StringLogger getStringLogger();
        Config getConfig();
    }
}
