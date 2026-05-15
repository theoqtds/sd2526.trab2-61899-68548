package sd2526.trab.impl.utils;

import com.google.gson.Gson;
import sd2526.trab.api.Message;
import sd2526.trab.impl.java.servers.JavaMessages;

import java.util.List;

public class LocalOperation {
    protected final String method;
    protected final List<String> parameters;
    protected final long sequenceNo;

    public LocalOperation(String method, List<String> parameters, long sequenceNo) {
        this.method = method;
        this.parameters = parameters;
        this.sequenceNo = sequenceNo;
    }

    public void execute() {
        JavaMessages javaMessages = JavaMessages.getInstance();

        switch (method) {
            case "deliverToKnownLocalRecipients" -> {
                String addressesJson = parameters.get(0);
                List<String> addresses = new Gson().fromJson(addressesJson, List.class); //could cause errors

                String msgJson = parameters.get(1);
                Message msg = new Gson().fromJson(msgJson, Message.class);

                javaMessages.deliverToKnownLocalRecipients(addresses, msg);
            }
            case  "reportUnknownLocalRecipients" -> {
                String addressesJson = parameters.get(0);
                List<String> addresses = new Gson().fromJson(addressesJson, List.class); //could cause errors

                String msgJson = parameters.get(1);
                Message msg = new Gson().fromJson(msgJson, Message.class);

                javaMessages.reportUnknownLocalRecipients(addresses, msg);
            }
            case "removeInboxMessage" -> {
                String name = parameters.get(0);
                String mid = parameters.get(1);
                String pwd = parameters.get(2);

                javaMessages.removeInboxMessage(name, mid, pwd);
            }
            case "deleteFromLocalInbox" -> {
                String mid = parameters.get(0);

                javaMessages.deleteFromLocalInbox(mid);
            }
            case "remoteDeleteUserInbox" -> {
                String mid = parameters.get(0);

                javaMessages.remoteDeleteUserInbox(mid);
            }
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        }
    }
}
