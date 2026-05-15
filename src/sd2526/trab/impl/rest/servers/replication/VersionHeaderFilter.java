package sd2526.trab.impl.rest.servers.replication;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS response filter that injects the X-MESSAGES-VERSION header into
 * every outgoing response.
 *
 * Because JAX-RS resource methods with non-Response return types cannot set
 * headers directly, the resource stores the current version in a ThreadLocal
 * and this filter picks it up.
 *
 * Register this class in both PrimaryMessagesServer and SecondaryMessagesServer
 * via  config.register(VersionHeaderFilter.class).
 */
@Provider
public class VersionHeaderFilter implements ContainerResponseFilter {

    private static final ThreadLocal<Long> currentVersion = new ThreadLocal<>();

    /** Called by resource methods to record the version for this request thread. */
    public static void setCurrentVersion(long version) {
        currentVersion.set(version);
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {

        // Always attach the current version (even for reads, so the client
        // knows the version it observed).
        long v = ReplicationState.getInstance().getVersion();

        Long override = currentVersion.get();
        if (override != null) {
            v = override;
            currentVersion.remove(); // clean up ThreadLocal
        }

        responseContext.getHeaders().add(
                PrimaryMessagesResource.VERSION_HEADER, String.valueOf(v));
    }
}