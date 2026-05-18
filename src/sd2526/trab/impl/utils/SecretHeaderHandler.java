package sd2526.trab.impl.utils;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import sd2526.trab.api.rest.RestMessages;

public class SecretHeaderHandler implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().add(RestMessages.HEADER_SECRET, ReplicationManager.getInstance().getSecret());
    }
}
