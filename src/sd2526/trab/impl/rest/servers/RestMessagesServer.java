package sd2526.trab.impl.rest.servers;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.utils.Secret;
import sd2526.trab.impl.utils.SecretHeaderHandler;

public class RestMessagesServer extends AbstractRestServer {
	public static final int PORT = 4567;

	//por motivos de debug iremos manter o logger - perguntar pro theo
	private static Logger Log = Logger.getLogger(RestMessagesServer.class.getName());

	RestMessagesServer() throws UnknownHostException {
		super(Log, Messages.SERVICE_NAME, PORT);
	}

	@Override
	protected void registerResources(ResourceConfig config) {
		config.register(RestMessagesResource.class);
		config.register(SecretHeaderHandler.class);
	}

	public static void main(String[] args) {
		try {
			String secret = args[0];
			Secret.init(secret);

			new RestMessagesServer().start();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}