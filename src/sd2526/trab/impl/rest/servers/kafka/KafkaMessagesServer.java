package sd2526.trab.impl.rest.servers.kafka;

import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.rest.servers.AbstractRestServer;
import sd2526.trab.impl.utils.*;
import sd2526.trab.impl.utils.replication.ReplicationManager;
import sd2526.trab.impl.utils.replication.VersionHeaderHandler;

import java.net.UnknownHostException;
import java.util.logging.Logger;

public class KafkaMessagesServer extends AbstractRestServer {
    public static final int PORT = 4567;

    private static Logger Log = Logger.getLogger(KafkaMessagesServer.class.getName());

    KafkaMessagesServer() throws UnknownHostException {
        super(Log, Messages.SERVICE_NAME, PORT);
    }

    @Override
    protected void registerResources(ResourceConfig config) {
        config.registerInstances(new KafkaMessagesResource());
        config.register(VersionHeaderHandler.class);
        config.register(SecretHeaderHandler.class);
    }

    public static void main(String[] args) {
        try {
            String topic = IP.domain();
            String secret = args[0];
            String kafkaAddress = args[1];
            Secret.init(secret);
            ReplicationManager.init(topic, kafkaAddress);

            new KafkaMessagesServer().start();
        } catch (Throwable e) {
            System.err.println("FATAL ERROR:");
            e.printStackTrace(System.err);
        }
    }
}
