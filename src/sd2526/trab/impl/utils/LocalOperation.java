package sd2526.trab.impl.utils;

import com.google.gson.Gson;
import sd2526.trab.api.Message;
import sd2526.trab.impl.java.servers.JavaMessages;

import java.util.List;

public class LocalOperation {
    protected final String method;
    protected final List<String> parameters;

    public LocalOperation(String method, List<String> parameters) {
        this.method = method;
        this.parameters = parameters;
    }

    public String execute() {
        JavaMessages javaMessages = JavaMessages.getInstance();

        switch (method) {
            case "deliverToKnownLocalRecipients" -> {
                String addressesJson = parameters.get(0);
                List<String> addresses = new Gson().fromJson(addressesJson, List.class); //could cause errors

                String msgJson = parameters.get(1);
                Message msg = new Gson().fromJson(msgJson, Message.class);

                javaMessages.deliverToKnownLocalRecipients(addresses, msg);
                return null;
            }
            case  "reportUnknownLocalRecipients" -> {
                String addressesJson = parameters.get(0);
                List<String> addresses = new Gson().fromJson(addressesJson, List.class); //could cause errors

                String msgJson = parameters.get(1);
                Message msg = new Gson().fromJson(msgJson, Message.class);

                javaMessages.reportUnknownLocalRecipients(addresses, msg);
                return null;
            }
            case "deleteFromLocalInbox" -> {
                String mid = parameters.get(0);

                javaMessages.deleteFromLocalInbox(mid);
                return null;
            }
            case "remoteDeleteUserInbox" -> {
                String mid = parameters.get(0);

                javaMessages.remoteDeleteUserInbox(mid);
                return null;
            }
            case "deleteFromInboxEntry" -> {
                String name = parameters.get(0);
                String mid = parameters.get(1);
                javaMessages.deleteFromInboxEntry(name, mid);
                return null;
            }
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        }
    }
}
