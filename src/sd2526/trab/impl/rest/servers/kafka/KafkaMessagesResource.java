package sd2526.trab.impl.rest.servers.kafka;

import java.util.List;

import com.google.gson.Gson;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.impl.api.rest.RestAdminMessages;
import sd2526.trab.impl.java.servers.JavaMessages;
import sd2526.trab.api.rest.KafkaMessages;
import sd2526.trab.impl.rest.servers.RestResource;
import sd2526.trab.impl.utils.replication.ReplicationManager;
import sd2526.trab.impl.utils.replication.VersionHeaderHandler;

@Singleton
@Provider
public class KafkaMessagesResource extends RestResource implements RestMessages, RestAdminMessages {
    JavaMessages javaMessages;

    synchronized JavaMessages javaMessages() {
        if (javaMessages == null)
            javaMessages = JavaMessages.getInstance();
        return javaMessages;
    }

    private Messages impl() {
        return javaMessages();
    }

    private KafkaMessages kafkaImpl() {
        return javaMessages();
    }

    Gson gson = new Gson();

    @Context
    HttpHeaders headers;

    public KafkaMessagesResource() {}

    @Override
    public String postMessage(String pwd, Message msg) {
        JavaMessages.PreparedPost prepared = super.resultOrThrow(kafkaImpl().prepareKafkaPost(pwd, msg));

        long offset = ReplicationManager.getInstance().publish(
                "deliverToKnownLocalRecipients",
                List.of(gson.toJson(prepared.knownAddresses()), gson.toJson(prepared.msg()))
        );

        kafkaImpl().handleKafkaPostSideEffects(prepared);

        ReplicationManager.getInstance().waitForVersion(offset);
        return prepared.msg().getId();
    }

    @Override
    public Message getMessage(String name, String mid, String pwd) {
        waitForClientVersion();
        return super.resultOrThrow( impl().getInboxMessage(name, mid, pwd));
    }

    @Override
    public List<String> getMessages(String name, String pwd, String query) {
        waitForClientVersion();
        if( query != null && ! query.isEmpty() )
            return super.resultOrThrow( impl().searchInbox(name, pwd, query));
        else
            return super.resultOrThrow(impl().getAllInboxMessages(name, pwd));
    }

    @Override
    public void removeFromUserInbox(String name, String mid, String pwd) {
        super.resultOrThrow(kafkaImpl().getUser(name, pwd));

        long offset = ReplicationManager.getInstance().publish(
                "deleteFromInboxEntry",
                List.of(name, mid)
        );
        ReplicationManager.getInstance().waitForVersion(offset);
    }

    @Override
    public void deleteMessage(String name, String mid, String pwd) {
        Message msg = super.resultOrThrow(kafkaImpl().prepareKafkaDelete(name, mid, pwd));

        long offset = ReplicationManager.getInstance().publish(
                "deleteFromLocalInbox",
                List.of(msg.getId())
        );

        kafkaImpl().handleKafkaDeleteSideEffects(msg);

        ReplicationManager.getInstance().waitForVersion(offset);
    }

    @Override
    public void remotePostMessage(Message m) {
        long offset = ReplicationManager.getInstance().publish(
                "deliverToKnownLocalRecipients",
                List.of(gson.toJson(kafkaImpl().getLocalRecipientAddresses(m)), gson.toJson(m))
        );
        ReplicationManager.getInstance().waitForVersion(offset);
    }

    @Override
    public void remoteDeleteMessage(String mid) {
        long offset = ReplicationManager.getInstance().publish(
                "deleteFromLocalInbox",
                List.of(mid)
        );
        ReplicationManager.getInstance().waitForVersion(offset);
    }

    @Override
    public void remoteDeleteUserInbox(String name) {
        long offset = ReplicationManager.getInstance().publish(
                "remoteDeleteUserInbox",
                List.of(name)
        );
        ReplicationManager.getInstance().waitForVersion(offset);
    }

    private void waitForClientVersion() {
        Long clientVersion = VersionHeaderHandler.version.get();
        if( clientVersion != null ) {
            ReplicationManager.getInstance().waitForVersion(clientVersion);
        }
    }
}

