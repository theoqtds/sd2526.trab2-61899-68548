package sd2526.trab.impl.rest.servers.replication;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.rest.servers.AbstractRestServer;

/**
 * Secondary Messages server.
 *
 * Launch arguments:
 *   args[0] = shared server secret  (must match the primary's secret)
 *
 * Example:
 *   java SecondaryMessagesServer mySecret
 *
 * Secondaries listen on PORT 4568 by default so they can run alongside a
 * primary on the same host during testing.  In a real deployment every replica
 * lives on its own host and all of them can use the same PORT as the primary.
 */
public class SecondaryMessagesServer extends AbstractRestServer {

    public static final int PORT = 4568;

    private static final Logger Log = Logger.getLogger(SecondaryMessagesServer.class.getName());

    SecondaryMessagesServer() throws UnknownHostException {
        super(Log, Messages.SERVICE_NAME, PORT);
    }

    @Override
    protected void registerResources(ResourceConfig config) {
        config.register(PrimaryMessagesResource.class);
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: SecondaryMessagesServer <secret>");
                System.exit(1);
            }

            String secret = args[0];

            ReplicationState state = ReplicationState.getInstance();
            state.setPrimary(false);
            state.setServerSecret(secret);

            Log.info("Starting SECONDARY");

            new SecondaryMessagesServer().start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}