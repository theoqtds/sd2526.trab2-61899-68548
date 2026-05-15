package sd2526.trab.impl.rest.servers.replication;

import java.util.List;
import java.util.logging.Logger;

import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.api.rest.RestAdminMessages;
import sd2526.trab.impl.java.servers.JavaMessages;
import sd2526.trab.impl.rest.servers.RestResource;

/**
 * JAX-RS resource for SECONDARY replicas.
 *
 * Reads:   served locally after checking the monotonic-read version.
 * Writes:  REJECTED for client-originated calls (returns 403).
 *          Accepted ONLY from the primary via the internal endpoint
 *          {@value #INTERNAL_PATH}.
 *
 * The secondary exposes an extra path for the primary to push updates:
 *   PUT /rest/internal/version/{v}   – updates the local version counter
 *
 * For a full implementation you would also push the actual operations so the
 * secondary can replay them; the version endpoint is the minimal skeleton.
 */
@Singleton
@Path("/")
public class SecondaryMessagesResource extends RestResource implements RestMessages, RestAdminMessages {

    private static final Logger Log = Logger.getLogger(SecondaryMessagesResource.class.getName());

    /** Base path for primary → secondary internal calls. */
    public static final String INTERNAL_PATH = "internal";

    /** How long (ms) to wait for the local version to catch up on a read. */
    private static final long VERSION_WAIT_TIMEOUT_MS = 5_000;

    @Context
    HttpHeaders httpHeaders;

    private Messages impl;

    private synchronized Messages impl() {
        if (impl == null)
            impl = JavaMessages.getInstance();
        return impl;
    }

    // -------------------------------------------------------------------------
    // Internal endpoint – called by the primary to update version
    // -------------------------------------------------------------------------

    /**
     * PUT /rest/internal/version/{v}
     *
     * The primary calls this after every successful write to notify the
     * secondary of the new version.  In a richer implementation the primary
     * would also send the operation payload here.
     */
    @PUT
    @Path(INTERNAL_PATH + "/version/{v}")
    @Produces(MediaType.APPLICATION_JSON)
    public void receiveVersion(@PathParam("v") long v) {
        checkServerSecret();
        ReplicationState.getInstance().updateVersion(v);
        Log.fine("Version updated to " + v);
    }

    // -------------------------------------------------------------------------
    // RestMessages – read operations  (served locally)
    // -------------------------------------------------------------------------

    @Override
    public Message getMessage(String name, String mid, String pwd) {
        waitForClientVersion();
        return super.resultOrThrow(impl().getInboxMessage(name, mid, pwd));
    }

    @Override
    public List<String> getMessages(String name, String pwd, String query) {
        waitForClientVersion();
        if (query != null && !query.isEmpty())
            return super.resultOrThrow(impl().searchInbox(name, pwd, query));
        else
            return super.resultOrThrow(impl().getAllInboxMessages(name, pwd));
    }

    // -------------------------------------------------------------------------
    // RestMessages – write operations  (rejected for clients)
    // -------------------------------------------------------------------------

    @Override
    public String postMessage(String pwd, Message msg) {
        throw new WebApplicationException(
                "Write operations must be directed to the primary", Status.FORBIDDEN);
    }

    @Override
    public void removeFromUserInbox(String name, String mid, String pwd) {
        throw new WebApplicationException(
                "Write operations must be directed to the primary", Status.FORBIDDEN);
    }

    @Override
    public void deleteMessage(String name, String mid, String pwd) {
        throw new WebApplicationException(
                "Write operations must be directed to the primary", Status.FORBIDDEN);
    }

    // -------------------------------------------------------------------------
    // RestAdminMessages – server-to-server write operations
    // (only accepted from other servers, not from clients)
    // -------------------------------------------------------------------------

    @Override
    public void remotePostMessage(Message m) {
        checkServerSecret();
        super.resultOrThrow(((AdminMessages) impl()).remotePostMessage(m));
        ReplicationState.getInstance().incrementVersion();
    }

    @Override
    public void remoteDeleteMessage(String mid) {
        checkServerSecret();
        super.resultOrThrow(((AdminMessages) impl()).remoteDeleteMessage(mid));
        ReplicationState.getInstance().incrementVersion();
    }

    @Override
    public void remoteDeleteUserInbox(String name) {
        checkServerSecret();
        super.resultOrThrow(((AdminMessages) impl()).remoteDeleteUserInbox(name));
        ReplicationState.getInstance().incrementVersion();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the X-MESSAGES-VERSION header sent by the client (the Tester
     * re-sends all X-MESSAGES-* headers it receives) and blocks until the
     * local state catches up to that version.
     */
    private void waitForClientVersion() {
        String headerValue = httpHeaders.getHeaderString(PrimaryMessagesResource.VERSION_HEADER);
        if (headerValue == null) return;
        try {
            long minVersion = Long.parseLong(headerValue);
            boolean ok = ReplicationState.getInstance().waitForVersion(minVersion, VERSION_WAIT_TIMEOUT_MS);
            if (!ok)
                Log.warning("Timed out waiting for version " + minVersion);
        } catch (NumberFormatException e) {
            // Ignore malformed header
        }
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