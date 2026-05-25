package sd2526.trab.impl.utils;

import io.grpc.*;

public class GrpcSecretClientInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> SECRET_KEY =
            Metadata.Key.of("server-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String secret = Secret.getInstance().getSecret();
                if (secret != null) {
                    headers.put(SECRET_KEY, secret);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
