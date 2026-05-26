package sd2526.trab.impl.rest.servers.zoho;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.java.servers.JavaZohoMessages;
import sd2526.trab.impl.rest.servers.AbstractRestServer;

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
            //tester puts the first arg as true to indicate that has erased all previous mails,
            //thats why we need this boolean variable
            boolean cleanState = Boolean.parseBoolean(args[0]);
            int i = 1;
            if (args[i].equals("true") || args[i].equals("false")) i++;

            String clientId     = args[i++];
            String clientSecret = args[i++];
            String refreshToken = args[i++];
            String accountId    = args[i++];
            String userEmail    = args[i];

            JavaZohoMessages.init(clientId, clientSecret, refreshToken, accountId, userEmail, cleanState);
            new ZohoMessagesServer().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}