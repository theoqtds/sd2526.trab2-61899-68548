package sd2526.trab.impl.rest.servers.replication;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.rest.servers.AbstractRestServer;

/**
 * Primary Messages server.
 *
 * Launch arguments:
 *   args[0] = shared server secret
 *   args[1..n] = URIs of the secondary replicas  (e.g. https://messages1.ourorg0:4568/rest)
 *
 * Example:
 *   java PrimaryMessagesServer mySecret https://messages1.ourorg0:4568/rest https://messages2.ourorg0:4568/rest
 */
public class PrimaryMessagesServer extends AbstractRestServer {

    // Primaries listen on a different port to avoid collisions when running
    // multiple replicas on the same host during testing.
    public static final int PORT = 4567;

    private static final Logger Log = Logger.getLogger(PrimaryMessagesServer.class.getName());

    PrimaryMessagesServer() throws UnknownHostException {
        super(Log, Messages.SERVICE_NAME, PORT);
    }

    @Override
    protected void registerResources(ResourceConfig config) {
        config.register(PrimaryMessagesResource.class);
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: PrimaryMessagesServer <secret> [<secondary-uri> ...]");
                System.exit(1);
            }

            String secret = args[0];

            // Collect secondary URIs (everything after the secret)
            List<String> secondaryURIs = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));

            // Configure shared replication state
            ReplicationState state = ReplicationState.getInstance();
            state.setPrimary(true);
            state.setServerSecret(secret);
            state.setReplicaURIs(secondaryURIs);

            Log.info("Starting PRIMARY – replicas: " + secondaryURIs);

            new PrimaryMessagesServer().start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}