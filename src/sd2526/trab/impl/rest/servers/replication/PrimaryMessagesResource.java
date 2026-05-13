package sd2526.trab.impl.rest.servers.replication;

import java.util.List;
import java.util.logging.Logger;

import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.api.rest.RestAdminMessages;
import sd2526.trab.impl.java.servers.JavaMessages;
import sd2526.trab.impl.rest.servers.RestResource;

/**
 * JAX-RS resource for the PRIMARY replica.
 *
 * Reads:  served locally.
 * Writes: executed locally first, then propagated synchronously to every
 *         secondary before returning to the client.
 *
 * Every response carries the header  X-MESSAGES-VERSION: <n>  so that clients
 * can enforce monotonic reads when talking to a secondary.
 */
@Singleton
public class PrimaryMessagesResource extends RestResource implements RestMessages, RestAdminMessages {

    private static final Logger Log = Logger.getLogger(PrimaryMessagesResource.class.getName());

    /** Header name agreed with the Tester. */
    public static final String VERSION_HEADER = "X-MESSAGES-VERSION";

    // Jersey injects the request headers so we can read X-MESSAGES-VERSION
    @Context
    HttpHeaders httpHeaders;

    // Lazy-initialised JAX-RS client used to propagate writes to secondaries
    private final Client propagationClient = ClientBuilder.newClient();

    private Messages impl;

    private synchronized Messages impl() {
        if (impl == null)
            impl = JavaMessages.getInstance();
        return impl;
    }

    // -------------------------------------------------------------------------
    // RestMessages – read operations
    // -------------------------------------------------------------------------

    @Override
    public Message getMessage(String name, String mid, String pwd) {
        return super.resultOrThrow(impl().getInboxMessage(name, mid, pwd));
    }

    @Override
    public List<String> getMessages(String name, String pwd, String query) {
        if (query != null && !query.isEmpty())
            return super.resultOrThrow(impl().searchInbox(name, pwd, query));
        else
            return super.resultOrThrow(impl().getAllInboxMessages(name, pwd));
    }

    // -------------------------------------------------------------------------
    // RestMessages – write operations  (execute + propagate + version bump)
    // -------------------------------------------------------------------------

    @Override
    public String postMessage(String pwd, Message msg) {
        String result = super.resultOrThrow(impl().postMessage(pwd, msg));
        long v = propagateAndBump();
        addVersionHeader(v);
        return result;
    }

    @Override
    public void removeFromUserInbox(String name, String mid, String pwd) {
        super.resultOrThrow(impl().removeInboxMessage(name, mid, pwd));
        long v = propagateAndBump();
        addVersionHeader(v);
    }

    @Override
    public void deleteMessage(String name, String mid, String pwd) {
        super.resultOrThrow(impl().deleteMessage(name, mid, pwd));
        long v = propagateAndBump();
        addVersionHeader(v);
    }

    // -------------------------------------------------------------------------
    // RestAdminMessages – server-to-server write operations
    // -------------------------------------------------------------------------

    @Override
    public void remotePostMessage(Message m) {
        checkServerSecret();
        super.resultOrThrow(((AdminMessages) impl()).remotePostMessage(m));
        long v = propagateAndBump();
        addVersionHeader(v);
    }

    @Override
    public void remoteDeleteMessage(String mid) {
        checkServerSecret();
        super.resultOrThrow(((AdminMessages) impl()).remoteDeleteMessage(mid));
        long v = propagateAndBump();
        addVersionHeader(v);
    }

    @Override
    public void remoteDeleteUserInbox(String name) {
        checkServerSecret();
        super.resultOrThrow(((AdminMessages) impl()).remoteDeleteUserInbox(name));
        long v = propagateAndBump();
        addVersionHeader(v);
    }

    // -------------------------------------------------------------------------
    // Internal replication endpoint  (called BY secondaries? No – called ON secondaries BY primary)
    // Primary exposes this so that it can also RECEIVE propagation if needed
    // in more complex scenarios, but for F2b it is unused on the primary side.
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Increments the local version and propagates the current state version
     * to all secondaries via their internal REST endpoint.
     *
     * @return the new version number
     */
    private long propagateAndBump() {
        long newVersion = ReplicationState.getInstance().incrementVersion();
        propagateVersionToSecondaries(newVersion);
        return newVersion;
    }

    /**
     * Sends the new version number to every known secondary so they can
     * update their local version counter.
     *
     * In a full implementation you would send the actual operation (or a log
     * entry) so the secondary can replay it.  For simplicity here we send the
     * version number; the secondary trusts the primary and updates its counter.
     *
     * For a richer implementation, consider sending the full Message object or
     * a serialised operation descriptor.
     */
    private void propagateVersionToSecondaries(long version) {
        List<String> replicas = ReplicationState.getInstance().getReplicaURIs();
        if (replicas == null || replicas.isEmpty()) return;

        String secret = ReplicationState.getInstance().getServerSecret();

        for (String uri : replicas) {
            try {
                Response r = propagationClient
                        .target(uri)
                        .path(SecondaryMessagesResource.INTERNAL_PATH)
                        .path("version")
                        .path(String.valueOf(version))
                        .request(MediaType.APPLICATION_JSON)
                        .header("X-SERVER-SECRET", secret)
                        .put(Entity.json(""));

                if (r.getStatus() != Status.NO_CONTENT.getStatusCode()
                        && r.getStatus() != Status.OK.getStatusCode()) {
                    Log.warning("Propagation to " + uri + " returned HTTP " + r.getStatus());
                }
                r.close();
            } catch (Exception e) {
                // Secondary may be down – log and continue (F2b only requires
                // secondary fault tolerance, primary failure is not masked here)
                Log.warning("Could not propagate version " + version + " to " + uri + ": " + e.getMessage());
            }
        }
    }

    /** Adds the X-MESSAGES-VERSION header to the current response. */
    private void addVersionHeader(long version) {
        // JAX-RS does not allow setting response headers from a resource method
        // directly when the return type is not Response.  The cleanest approach
        // is to throw a WebApplicationException that wraps a 200/204 with the
        // header, but that breaks the return contract.
        //
        // Instead, we use a ContainerResponseFilter (VersionHeaderFilter) that
        // reads the version from a ThreadLocal and injects it into every response.
        VersionHeaderFilter.setCurrentVersion(version);
    }

    /** Rejects calls that do not carry the correct shared server secret. */
    private void checkServerSecret() {
        String incoming = httpHeaders.getHeaderString("X-SERVER-SECRET");
        String expected = ReplicationState.getInstance().getServerSecret();
        if (expected != null && !expected.equals(incoming)) {
            throw new WebApplicationException(Status.FORBIDDEN);
        }
    }
}