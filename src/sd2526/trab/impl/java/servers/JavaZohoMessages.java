package sd2526.trab.impl.java.servers;

import static sd2526.trab.api.java.Result.error;
import static sd2526.trab.api.java.Result.ok;
import static sd2526.trab.api.java.Result.ErrorCode.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import sd2526.trab.api.Message;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.java.clients.Clients;

public class JavaZohoMessages extends JavaBaseService implements Messages, AdminMessages {

    private static final Logger Log = Logger.getLogger(JavaZohoMessages.class.getName());


    private static final String ZOHO_BASE_URL = "https://mail.zoho.eu/api/accounts/";
    private static final int REMOTE_COMM_DEADLINE = 90000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OAuth20Service oauthService;

    private final String accountId;
    private final String userEmail;

    private OAuth2AccessToken accessToken;

    private static JavaZohoMessages instance;

    private JavaZohoMessages(
            String clientId,
            String clientSecret,
            String refreshToken,
            String accountId,
            String userEmail,
            boolean cleanState) {

        this.accountId = accountId;
        this.userEmail = userEmail;

        try {
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                    (hostname, session) -> true);

            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    }
            }, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure SSL", e);
        }
        this.oauthService = new ServiceBuilder(clientId)
                .apiSecret(clientSecret)
                .build(new DefaultApi20() {

                    //needed to scribe (from poms) know where to make the calls
                    @Override
                    public String getAccessTokenEndpoint() {
                        return "https://accounts.zoho.eu/oauth/v2/token";
                    }

                    @Override
                    protected String getAuthorizationBaseUrl() {
                        return "https://accounts.zoho.eu/oauth/v2/auth";
                    }
                });

        try {
            this.accessToken =
                    oauthService.refreshAccessToken(refreshToken);
        } catch (Exception e) {
            throw new RuntimeException( "Failed to get Zoho access token: " + e.getMessage(), e);
        }

        if (cleanState) {
            try {
                deleteAllEmails();
            } catch (Exception e) { Log.warning("Failed to clean Zoho inbox: " + e.getMessage());
            }
        }
    }

    public static synchronized void init(
            String clientId,
            String clientSecret,
            String refreshToken,
            String accountId,
            String userEmail,
            boolean cleanState) {

        instance = new JavaZohoMessages(
                clientId,
                clientSecret,
                refreshToken,
                accountId,
                userEmail,
                cleanState);
    }

    public static synchronized JavaZohoMessages getInstance() {
        return instance;
    }

    private JsonNode zohoGet(String url) throws Exception {

        OAuthRequest req = new OAuthRequest(Verb.GET, url);

        oauthService.signRequest(accessToken, req);

        Response resp = oauthService.execute(req);

        return mapper.readTree(resp.getBody());
    }

    private JsonNode zohoPost(String url, String body) throws Exception {

        OAuthRequest req = new OAuthRequest(Verb.POST, url);

        req.addHeader("Content-Type", "application/json");
        req.setPayload(body);

        oauthService.signRequest(accessToken, req);
        Response resp = oauthService.execute(req);
        return mapper.readTree(resp.getBody());
    }


    private void zohoDelete(String url) throws Exception {

        OAuthRequest req = new OAuthRequest(Verb.DELETE, url);
        oauthService.signRequest(accessToken, req);
        oauthService.execute(req);
    }


    /**
     * Deletes all emails from zoho inbox, it is triggered when the cleanSlate = true
     */
    private void deleteAllEmails() throws Exception {
        String url = ZOHO_BASE_URL + accountId + "/messages/view?limit=200";
        JsonNode root = zohoGet(url);
        JsonNode data = root.path("data");

        if (data.isArray()) {
            for (JsonNode email : data) {
                String msgId = email.path("messageId").asText();
                zohoDelete(ZOHO_BASE_URL + accountId + "/folders/8634380000000002014/messages/" + msgId);
            }
        }
    }

    /**
     * Stores a system message in zoho as a mail. All the msg contents are stored in the mail "body"
     * @param msg - all the details about a msg (id, sender, etc)
     * @throws Exception
     */
    private void storeInZoho(Message msg) throws Exception {
        Map<String, Object> emailBody = new HashMap<>();
        emailBody.put("fromAddress", userEmail);
        emailBody.put("toAddress", userEmail);

        String metaSubject = "SD2526|" + msg.getId() + "|" + msg.getSender() + "|"
                + msg.getCreationTime() + "|" + String.join(",", msg.getDestination()) + "|"
                + (msg.getSubject() == null ? "" : msg.getSubject());

        emailBody.put("subject", metaSubject);
        emailBody.put("content", msg.getContents() == null ? "" : msg.getContents());

        String json = mapper.writeValueAsString(emailBody);
        zohoPost(ZOHO_BASE_URL + accountId + "/messages", json);
    }

    /**
     * returns all messages stored in zoho. Only the emails that starts with "SD2526" are considered system messages.
     * @return a list that contains all valid system messages stored in zoho.
     * @throws Exception if an error occurs while communicating with the zoho API.
     */
    private List<Message> getAllFromZoho() throws Exception {
        String url = ZOHO_BASE_URL + accountId + "/messages/view?limit=200";
        JsonNode root = zohoGet(url);
        JsonNode data = root.path("data");

        Map<String, Message> byId = new LinkedHashMap<>();
        if (data.isArray()) {
            for (JsonNode email : data) {
                //debug to check if the emails are in a folder
                Log.info("email folderId: " + email.path("folderId").asText(""));
                String subject = email.path("subject").asText("");
                if (!subject.startsWith("SD2526|")) continue;
                String summary = email.path("summary").asText("");
                Message msg = decodeFromSubject(subject, summary);
                if (msg == null || msg.getId() == null) continue;
                Message existing = byId.get(msg.getId());
                if (existing == null || msg.getDestination().size() > existing.getDestination().size())
                    byId.put(msg.getId(), msg);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * helper method that decodes a message from a zoho email subject and summary.
     * @param subject - the email subject with the encoded metadata.
     * @param summary - the email summary with the message content.
     * @return the DECODED message, null if the format is invalid
     */
    private Message decodeFromSubject(String subject, String summary) {
        try {
            //transforms html characters returned by zoho back into the original form.
            subject = subject
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"");

            String[] parts = subject.split("\\|", 6);
            if (parts.length < 5) return null;

            Message msg = new Message();
            msg.setId(parts[1]);
            msg.setSender(parts[2].replace("&lt;", "<").replace("&gt;", ">"));
            msg.setCreationTime(Long.parseLong(parts[3]));
            msg.setDestination(new HashSet<>(Arrays.asList(parts[4].split(","))));
            msg.setSubject(parts.length > 5 ? parts[5] : "");
            msg.setContents(summary);

            return msg;
        } catch (Exception e) {
            Log.warning("Failed to decode from subject: " + e.getMessage());
            return null;
        }
    }


    /**
     * Deletes all emails associated with this messageId from zoho
     *
     * @param mid - messageId
     * @throws Exception if an error occurs while communicating with zoho API
     */
    private void deleteFromZoho(String mid) throws Exception {
        String url = ZOHO_BASE_URL + accountId + "/messages/view?limit=200";
        JsonNode root = zohoGet(url);
        JsonNode data = root.path("data");

        if (data.isArray()) {
            for (JsonNode email : data) {
                String subject = email.path("subject").asText("");
                if (subject.startsWith("SD2526|" + mid + "|")) {
                    String zohoMsgId = email.path("messageId").asText();
                    zohoDelete(ZOHO_BASE_URL + accountId + "/folders/8634380000000002014/messages/" + zohoMsgId);
                }
            }
        }
    }


    /**
     * Publishes a message sent by a local user. The message is sent to local and remote storages.
     * @param pwd password of the user posting the message
     *
     * @param msg the message object to be posted to the server
     * @return the identifier of the stored message
     */
    @Override
    public Result<String> postMessage(String pwd, Message msg) {

        Log.info("postMessage : pwd=%s, msg=%s".formatted(pwd, msg));

        if (msg.getDestination() == null || msg.getDestination().isEmpty()) {
            return error(BAD_REQUEST);
        }

        Result<User> res = getUser(msg.getSender(), pwd);
        if (!res.isOK()) {
            return error(res.error());
        }

        return doPost(res.value(), msg);
    }


    /**
     * Gives an ID and sender to the message.
     * The message is stored for local servers and forwarded to remote domains.
     * @param sender - the authenticated sender of the message.
     * @param msg - the message.
     * @return the id of the message posted.
     */
    private Result<String> doPost(User sender, Message msg) {

        msg.setId(THIS_DOMAIN + "+" + UUID.randomUUID().toString());

        msg.setSender(
                "%s <%s@%s>".formatted(
                        sender.getDisplayName(),
                        sender.getName(),
                        sender.getDomain()));

        var localAddresses =
                getLocalRecipientAddresses(msg);
        var remoteAddresses =
                getRemoteRecipientAddresses(msg);

        //check the destination files of local and remote adresses
        Log.info("Local Recipients: " + localAddresses);
        Log.info("Remote Recipients: " + remoteAddresses);

        if (!localAddresses.isEmpty()) {
            try {
                storeInZoho(msg);
            } catch (Exception e) {
                Log.warning(
                        "Failed to store in Zoho: "
                                + e.getMessage());

                return error(INTERNAL_ERROR);
            }
        }

        if (!remoteAddresses.isEmpty()) {

            var remoteTargets =
                    remoteAddresses.stream().collect(
                            Collectors.groupingBy(
                                    super::getDomain,
                                    Collectors.toSet()));

            for (var entry : remoteTargets.entrySet()) {

                var domain = entry.getKey();
                var addresses = entry.getValue();

                var res =
                        super.reTry(
                                () ->
                                        Clients.AdminMessagesClient
                                                .get(domain)
                                                .remotePostMessage(msg),
                                REMOTE_COMM_DEADLINE);

                if (res.error() == ErrorCode.TIMEOUT) {
                    for (var addr : addresses) {
                        try {
                            storeInZoho(
                                    msg.cloneWithTimeout(addr));
                        } catch (Exception ex) {
                            Log.warning(
                                    "Failed to store timeout msg: "
                                            + ex.getMessage());
                        }
                    }
                }
            }
        }
        return ok(msg.getId());
    }

    /**
     * Retrieves a specific message from the user`s inbox, identified by messageId (mid)
     * @param name the owner of the inbox
     * @param mid the identifier of the message to be retrieved
     * @param pwd password of the owner of the inbox
     * @return ok if the user was found and is a recipient.
     *          NOT_FOUND if the message with the given mid does not exist in the user`s inbox.
     *          INTERNAL_ERROR if the zoho API call fails.
     *
     */
    @Override
    public Result<Message> getInboxMessage(
            String name,
            String mid,
            String pwd) {

        return getUser(name, pwd).thenWith(u -> {

            try {
                String userAddress =
                        name + "@" + THIS_DOMAIN;

                for (Message m : getAllFromZoho()) {
                    if (mid.equals(m.getId())
                            && m.getDestination() != null
                            && m.getDestination()
                            .contains(userAddress))
                        return ok(m);
                }
                return error(NOT_FOUND);
            } catch (Exception e) {
                e.printStackTrace();
                return error(INTERNAL_ERROR);
            }
        });
    }


    /**
     * Returns the identifiers of all messages in the user's inbox.
     *
     * @param name - the user name.
     * @param pwd - the user password.
     * @return a list containing all inbox message identifiers.
     */
    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        return getUser(name, pwd).thenWith(u -> {
            try {
                String userAddress = name + "@" + THIS_DOMAIN;
                List<Message> all = getAllFromZoho();
                //check if all the emaisl are being received as supposed to, and their details
                Log.info("Total emails in Zoho: " + all.size());
                for (Message m : all) {
                    Log.info("  msg id=" + m.getId() + " dest=" + m.getDestination());
                }

                List<String> ids = all.stream()
                        .filter(m -> m.getDestination() != null
                                && m.getDestination().contains(userAddress))
                        .map(Message::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                //check if filtered right
                Log.info("Filtered for " + userAddress + ": " + ids);
                return ok(ids);
            } catch (Exception e) {
                e.printStackTrace();
                return error(INTERNAL_ERROR);
            }
        });
    }

    /**
     * Searches the user`s inbox for messages whose subject or content contains the given query.
     *
     * @param name - the user name.
     * @param pwd - the user password.
     * @param query - the search query.
     * @return a list containing the identifiers of matching messages.
     */
    @Override
    public Result<List<String>> searchInbox(
            String name,
            String pwd,
            String query) {

        return getUser(name, pwd).thenWith(u -> {

            try {
                String q = query.toUpperCase();
                String userAddress =
                        name + "@" + THIS_DOMAIN;

                List<String> ids =
                        getAllFromZoho().stream()
                                .filter(m ->
                                        m.getDestination() != null
                                                && m.getDestination()
                                                .contains(userAddress))
                                .filter(m ->

                                        (m.getSubject() != null
                                                && m.getSubject()
                                                .toUpperCase()
                                                .contains(q))
                                                ||
                                                (m.getContents() != null
                                                        && m.getContents()
                                                        .toUpperCase()
                                                        .contains(q)))

                                .map(Message::getId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                return ok(ids);
            } catch (Exception e) {
                e.printStackTrace();
                return error(INTERNAL_ERROR);
            }
        });
    }

    /**
     * Removes a message from the user`s inbox.
     * If the message has additional recipients, we re-store it without this user.
     *
     * @param name - the user name.
     * @param mid - the message identifier.
     * @param pwd - the user password.
     * @return a successful result if the operation completes successfully.
     */
    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        return getUser(name, pwd).thenWith(u -> {
            try {
                String userAddress = name + "@" + THIS_DOMAIN;
                String url = ZOHO_BASE_URL + accountId + "/messages/view?limit=200";
                JsonNode root = zohoGet(url);
                JsonNode data = root.path("data");

                if (data.isArray()) {
                    for (JsonNode email : data) {
                        String subject = email.path("subject").asText("");
                        if (!subject.startsWith("SD2526|" + mid + "|")) continue;

                        String zohoMsgId = email.path("messageId").asText();
                        String summary = email.path("summary").asText("");
                        Message msg = decodeFromSubject(subject, summary);

                        if (msg == null || !msg.getDestination().contains(userAddress)) continue;

                        // apaga este email
                        zohoDelete(ZOHO_BASE_URL + accountId + "/folders/8634380000000002014/messages/" + zohoMsgId);

                        // se ainda ha outros destinatarios, guarda sem este
                        msg.getDestination().remove(userAddress);
                        if (!msg.getDestination().isEmpty())
                            storeInZoho(msg);

                        return ok();
                    }
                }
                return error(NOT_FOUND);
            } catch (Exception e) {
                e.printStackTrace();
                return error(INTERNAL_ERROR);
            }
        });
    }


    /**
     * Deletes a message sent by the user from all recipient domains.
     *
     * @param name - the sender user name.
     * @param mid - the message identifier.
     * @param pwd - the sender password.
     * @return a successful result if the operation completes successfully.
     */
    @Override
    public Result<Void> deleteMessage(
            String name,
            String mid,
            String pwd) {

        Log.info("deleteMessage : name=" + name + ", mid=" + mid);

        return getUser(name, pwd).thenWith(u -> {

            try {
                Message found = null;
                for (Message m : getAllFromZoho())
                    if (mid.equals(m.getId())) {
                        found = m;
                        break;
                    }

                if (found == null) {
                    return error(NOT_FOUND);
                }

                if (!name.equals (getName(found.senderAddress()))){
                    return error(FORBIDDEN);
                }
                Message finalFound = found;

                var domains =
                        found.getDestination().stream()
                                .map(super::getDomain)
                                .collect(Collectors.toSet());

                for (var domain : domains) {
                    if (domain.equals(THIS_DOMAIN))
                        deleteFromZoho(mid);
                    else
                        new Thread(() ->
                                super.reTry(
                                        () ->
                                                Clients.AdminMessagesClient
                                                        .get(domain)
                                                        .remoteDeleteMessage(
                                                                finalFound.getId()),
                                        REMOTE_COMM_DEADLINE))
                                .start();
                }
                return ok();
            } catch (Exception e) {
                e.printStackTrace();
                return error(INTERNAL_ERROR);
            }
        });
    }


    /**
     * Stores a message received from another message server.
     *
     * @param msg - the message to be stored
     * @return a successful result if the operation completes successfully.
     */
    @Override
    public Result<Void> remotePostMessage(Message msg) {
        //register if a message has arrived in the other server
        Log.info("remotePostMessage called: " + msg.getId()
                + " dest=" + msg.getDestination()
                + " sender=" + msg.getSender());
        try {
            storeInZoho(msg);
            //confirmation if it has arrived
            Log.info("storeInZoho received " + msg.getId());
            return ok();
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }


    /**
     * Deletes a message at the request of another message server.
     *
     * @param mid the message identifier.
     * @return a successful result if the operation completes successfully.
     */
    @Override
    public Result<Void> remoteDeleteMessage(String mid) {
        try {
            deleteFromZoho(mid);
            return ok();

        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    /**
     * Removes all inbox messages of a given user.
     * If a message has additional recipients, it is re-stored without this user.
     *
     * @param name - the user name.
     * @return a successful result if the operation completes successfully.
     */
    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {

        Log.info("remoteDeleteUserInbox : name=" + name);

        try {

            String userAddress =
                    name + "@" + THIS_DOMAIN;

            List<Message> msgs =
                    getAllFromZoho();

            for (Message m : msgs) {
                if (m.getDestination() != null
                        && m.getDestination()
                        .contains(userAddress)) {

                    deleteFromZoho(m.getId());
                    m.getDestination()
                            .remove(userAddress);

                    if (!m.getDestination().isEmpty())
                        storeInZoho(m);
                }
            }

            return ok();

        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    /**
     * Authenticates a user via the Users service, extracting the username first.
     * @param user - the username of the user to authenticate.
     * @param pwd - the password used to validate the user
     * @return ok if the authentication succeeds
     *          INTERNAL_ERROR if the Users service cannot be reached.
     */
    public Result<User> getUser(
            String user,
            String pwd) {

        try {
            String addr = user;

            if (user.contains("<"))
                addr = user.substring(
                                user.indexOf("<") + 1,
                                user.indexOf(">"));

            var name =
                    addr.split("@")[0];

            return Clients.UsersClient
                    .get()
                    .getUser(name, pwd);

        } catch (Exception x) {
            x.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    /**
     * Returns the subset of message destinations that belong to this domain.
     */
    public List<String> getLocalRecipientAddresses(Message msg) {
        return msg.getDestination()
                .stream()
                .filter(super::isLocalAddress)
                .toList();
    }

    /**
     * Returns the subset of message destinations that belong to remote domains.
     */
    private Set<String> getRemoteRecipientAddresses(Message msg) {
        return msg.getDestination()
                .stream()
                .filter(Predicate.not(super::isLocalAddress))
                .collect(Collectors.toSet());
    }
}