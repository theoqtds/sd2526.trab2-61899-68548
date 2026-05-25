package sd2526.trab.impl.utils;

import io.grpc.*;

public class GrpcSecretServerInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> SECRET_KEY =
            Metadata.Key.of("server-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();

        if (fullMethodName.contains("Admin")) {
            String receivedSecret = headers.get(SECRET_KEY);
            String expectedSecret = Secret.getInstance().getSecret();

            if (expectedSecret == null || !expectedSecret.equals(receivedSecret)) {
                call.close(Status.PERMISSION_DENIED
                        .withDescription("Invalid or missing server secret."), new Metadata());
                return new ServerCall.Listener<>() {};
            }
        }

        return next.startCall(call, headers);
    }
}
