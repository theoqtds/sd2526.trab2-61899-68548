package sd2526.trab.impl.grpc.servers;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import sd2526.trab.api.java.Users;
import sd2526.trab.impl.utils.Secret;

public class GrpcUsersServer extends AbstractGrpcServer {
public static final int PORT = 13456;
	
	private static Logger Log = Logger.getLogger(GrpcUsersServer.class.getName());

	public GrpcUsersServer() throws Exception {
		super( Log, Users.SERVICE_NAME, PORT);
	}
	
	@Override
	protected List<GrpcController> controllers(String uri) {
		return List.of( new GrpcUsersController(), new GrpcAdminUsersController() );
	}
	
	public static void main(String[] args) {
		try {
			String secret = args[0];
			Secret.init(secret);

			new GrpcUsersServer().start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
