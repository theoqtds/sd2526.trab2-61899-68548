package sd2526.trab.impl.grpc.servers;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.utils.Secret;

public class GrpcMessagesServer extends AbstractGrpcServer {
public static final int PORT = 14567;
	
	private static Logger Log = Logger.getLogger(GrpcMessagesServer.class.getName());

	public GrpcMessagesServer() throws Exception {
		super( Log, Messages.SERVICE_NAME, PORT);
	}
	
	@Override
	protected List<GrpcController> controllers(String uri) {
		return List.of( new GrpcMessagesController(), new GrpcAdminMessagesController() );
	}
	
	public static void main(String[] args) {
		try {
			String secret = args[0];
			Secret.init(secret);

			new GrpcMessagesServer().start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}	
}
