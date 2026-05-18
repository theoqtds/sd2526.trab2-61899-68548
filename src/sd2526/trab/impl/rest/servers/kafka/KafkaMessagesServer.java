package sd2526.trab.impl.rest.servers.kafka;

import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.rest.servers.AbstractRestServer;
import sd2526.trab.impl.utils.IP;
import sd2526.trab.impl.utils.ReplicationManager;
import sd2526.trab.impl.utils.VersionHeaderHandler;

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
        config.registerInstances(new VersionHeaderHandler());
    }

    public static void main(String[] args) {
        try {
            String topic = IP.domain();
            String secret = args[0];
            String kafkaAddress = args[1];
            ReplicationManager.init(topic, kafkaAddress, secret);
            new KafkaMessagesServer().start();
        } catch (Throwable e) {
            System.err.println("FATAL ERROR:");
            e.printStackTrace(System.err);
        }
    }
}
