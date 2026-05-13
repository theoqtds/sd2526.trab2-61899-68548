package sd2526.trab.impl.rest.servers.replication;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds shared replication state: version counter and list of replica URIs.
 * Used by both Primary and Secondary servers.
 */
public class ReplicationState {

    private static ReplicationState instance;

    // Monotonically increasing version counter; incremented on every write
    private final AtomicLong version = new AtomicLong(0);

    // URIs of all OTHER replicas that the primary must propagate writes to.
    // For a secondary, this list is empty / not used.
    private List<String> replicaURIs;

    // Whether this instance is the primary
    private boolean isPrimary;

    // Shared secret used to authenticate server-to-server calls
    private String serverSecret;

    private ReplicationState() {}

    public static synchronized ReplicationState getInstance() {
        if (instance == null)
            instance = new ReplicationState();
        return instance;
    }

    // -------------------------------------------------------------------------
    // Version management
    // -------------------------------------------------------------------------

    /** Returns the current version. */
    public long getVersion() {
        return version.get();
    }

    /** Increments and returns the new version. Called by primary after every write. */
    public long incrementVersion() {
        return version.incrementAndGet();
    }

    /**
     * Updates the local version to {@code v} if {@code v} is greater than the
     * current value.  Called by a secondary when it receives a propagated write.
     */
    public void updateVersion(long v) {
        version.updateAndGet(current -> Math.max(current, v));
    }

    /**
     * Blocks (with a timeout) until the local version is >= {@code minVersion}.
     * Used by secondaries to satisfy monotonic-read guarantees.
     *
     * @param minVersion minimum version the caller needs
     * @param timeoutMs  maximum milliseconds to wait
     * @return true if the condition was met, false if timed out
     */
    public boolean waitForVersion(long minVersion, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (version.get() < minVersion) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;
            try {
                Thread.sleep(Math.min(50, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public List<String> getReplicaURIs() {
        return replicaURIs;
    }

    public void setReplicaURIs(List<String> uris) {
        this.replicaURIs = uris;
    }

    public String getServerSecret() {
        return serverSecret;
    }

    public void setServerSecret(String secret) {
        this.serverSecret = secret;
    }
}