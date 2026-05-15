package sd2526.trab.impl.rest.servers;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.api.java.Messages;

public class ZohoMessagesServer extends AbstractRestServer {

    public static final int PORT = 4567;

    private static Logger Log = Logger.getLogger(ZohoMessagesServer.class.getName());

    ZohoMessagesServer() throws UnknownHostException {
        super(Log, Messages.SERVICE_NAME, PORT);
    }

    @Override
    protected void registerResources(ResourceConfig config) {
        config.register(ZohoMessagesResource.class);
    }

    public static void main(String[] args) {
        try {
            // foamrt <clientId> <clientSecret> <refreshToken> <accountId> <userEmail>
            // change the way that we receive those args
            int offset = 0;
            boolean cleanState = args.length > 0 && Boolean.parseBoolean(args[0]);
            if (args.length > 1 && (args[1].equals("true") || args[1].equals("false"))) {
                offset = 1; // skips the bool - check later
            }
            String clientId     = args.length > 1 + offset ? args[1 + offset] : "";
            String clientSecret = args.length > 2 + offset ? args[2 + offset] : "";
            String refreshToken = args.length > 3 + offset ? args[3 + offset] : "";
            String accountId    = args.length > 4 + offset ? args[4 + offset] : "";
            String userEmail    = args.length > 5 + offset ? args[5 + offset] : "";
            try {
                JavaZohoMessages.init(clientId, clientSecret, refreshToken, accountId, userEmail, cleanState);
                new ZohoMessagesServer().start();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}