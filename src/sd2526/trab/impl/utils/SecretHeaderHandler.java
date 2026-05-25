package sd2526.trab.impl.utils;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.impl.api.rest.AdminSecret;

import java.util.logging.Logger;

@Provider
@AdminSecret
public class SecretHeaderHandler implements ClientRequestFilter, ContainerRequestFilter {

    private static Logger Log = Logger.getLogger(SecretHeaderHandler.class.getName());

    @Override
    public void filter(ClientRequestContext requestContext) {
        String secret = Secret.getInstance().getSecret();

        if (secret != null) {
            requestContext.getHeaders().add(RestMessages.HEADER_SECRET, secret);
        }
        System.out.println("Secret header sent");
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String receivedSecret = requestContext.getHeaders().getFirst(RestMessages.HEADER_SECRET);

        String expectedSecret = Secret.getInstance().getSecret();

        if (expectedSecret == null || !expectedSecret.equals(receivedSecret)) {
            Response response = Response.status(Response.Status.FORBIDDEN)
                    .entity("Invalid or missing server secret.")
                    .build();

            requestContext.abortWith(response);
        }
        System.out.println("Secret header received");
    }
}
