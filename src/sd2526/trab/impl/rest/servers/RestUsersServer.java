package sd2526.trab.impl.rest.servers;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.api.java.Users;
import sd2526.trab.impl.utils.Secret;
import sd2526.trab.impl.utils.SecretHeaderHandler;

public class RestUsersServer extends AbstractRestServer {
	public static final int PORT = 3456;
	
	private static Logger Log = Logger.getLogger(RestUsersServer.class.getName());

	RestUsersServer() throws UnknownHostException {
		super( Log, Users.SERVICE_NAME , PORT);
	}
	
	@Override
	protected void registerResources(ResourceConfig config) {
		config.register(RestUsersResource.class );
		config.register(SecretHeaderHandler.class);
	}
	
	public static void main(String[] args) {
		try {
			String secret = args[0];
			Secret.init(secret);

			new RestUsersServer().start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}	
}