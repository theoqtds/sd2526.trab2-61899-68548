package sd2526.trab.impl.utils;

import java.util.List;

public class Operation {
    protected final String method;
    protected final String domain;
    protected final List<String> parameters;
    protected final long sequenceNo;

    public Operation(String method, String domain, List<String> parameters, long sequenceNo) {
        this.method = method;
        this.domain = domain;
        this.parameters = parameters;
        this.sequenceNo = sequenceNo;
    }

    public void execute() {
        switch (method) {
            case "deliverToKnownLocalRecipients":
                break;
            case  "reportUnknownLocalRecipients":
                break;
            case "removeInboxMessage":
                break;
            case "deleteFromLocalInbox":
                break;
            case "remoteDeleteUserInbox":
                break;
        }
    }
}
