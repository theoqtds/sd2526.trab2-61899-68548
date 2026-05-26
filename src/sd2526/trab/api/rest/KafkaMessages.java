package sd2526.trab.api.rest;

import sd2526.trab.api.Message;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.impl.java.servers.JavaMessages;

import java.util.List;

public interface KafkaMessages {
    Result<JavaMessages.PreparedPost> prepareKafkaPost(String pwd, Message msg);

    void handleKafkaPostSideEffects(JavaMessages.PreparedPost prepared);

    Result<Message> prepareKafkaDelete(String name, String mid, String pwd);

    void handleKafkaDeleteSideEffects(Message msg);

    void deleteFromInboxEntry(String name, String mid);

    List<String> getLocalRecipientAddresses(Message msg);

    Result<User> getUser(String user, String pwd);
}
