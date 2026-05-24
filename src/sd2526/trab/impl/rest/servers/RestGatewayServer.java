package sd2526.trab.impl.rest.servers;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

public class RestGatewayServer extends AbstractRestServer {

	public static final int PORT = 6666;

	private static Logger Log = Logger.getLogger(RestGatewayServer.class.getName());

	RestGatewayServer() throws UnknownHostException {
		super(Log, null, PORT);
	}

	@Override
	protected void registerResources(ResourceConfig config) {
		config.registerInstances(new RestUsersResource(true), new RestMessagesResource(true));
	}

	public static void main(String[] args) {
		try {
			new RestGatewayServer().start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}