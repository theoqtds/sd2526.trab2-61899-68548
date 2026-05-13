package sd2526.trab.impl.rest.servers;

import static sd2526.trab.api.java.Result.error;
import static sd2526.trab.api.java.Result.ok;
import static sd2526.trab.api.java.Result.ErrorCode.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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
import sd2526.trab.impl.java.servers.JavaBaseService;

public class JavaZohoMessages extends JavaBaseService implements Messages, AdminMessages {

    private static final Logger Log = Logger.getLogger(JavaZohoMessages.class.getName());

    private static final String SEPARATOR = "\n------SD2526------\n";
    private static final String ZOHO_BASE = "https://mail.zoho.eu/api/accounts/";
    private static final int REMOTE_COMM_DEADLINE = 90000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OAuth20Service oauthService;

    private final String accountId;
    private final String userEmail;

    private final AtomicLong counter = new AtomicLong(0L);

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

            throw new RuntimeException(
                    "Failed to get Zoho access token: "
                            + e.getMessage(),
                    e);
        }

        if (cleanState) {

            Log.info("Clean state requested - deleting all emails in Zoho inbox");

            try {

                deleteAllEmails();

            } catch (Exception e) {

                Log.warning(
                        "Failed to clean Zoho inbox: "
                                + e.getMessage());
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

    // encoders/decoders

    private String encodeMessage(Message msg) {

        String dest =
                String.join(",", msg.getDestination());

        return (msg.getContents() == null ? "" : msg.getContents())
                + SEPARATOR
                + "id=" + msg.getId() + "\n"
                + "sender=" + msg.getSender() + "\n"
                + "creationTime=" + msg.getCreationTime() + "\n"
                + "destination=" + dest;
    }

    private Message decodeMessage(String body, String subject) {
        try {
            int sepIdx = body.indexOf(SEPARATOR);
            Log.info("decodeMessage sepIdx: " + sepIdx + " body length: " + body.length());

            if (sepIdx < 0)
                return null;

            String contents = body.substring(0, sepIdx);
            String meta = body.substring(sepIdx + SEPARATOR.length());

            Map<String, String> props = new HashMap<>();
            for (String line : meta.split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    props.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }

            Message msg = new Message();
            msg.setId(props.get("id"));
            msg.setSender(props.get("sender"));
            msg.setCreationTime(Long.parseLong(props.getOrDefault("creationTime", "0")));
            msg.setSubject(subject);
            msg.setContents(contents);

            String destStr = props.get("destination");
            if (destStr != null && !destStr.isEmpty()) {
                msg.setDestination(new HashSet<>(Arrays.asList(destStr.split(","))));
            }

            return msg;

        } catch (Exception e) {
            Log.warning("Failed to decode message: " + e.getMessage());
            return null;
        }
    }

    // part related to zoho inbox msgs

    private void deleteAllEmails() throws Exception {
        String url = ZOHO_BASE + accountId + "/messages/view?limit=200";
        JsonNode root = zohoGet(url);
        JsonNode data = root.path("data");

        if (data.isArray()) {
            for (JsonNode email : data) {
                String msgId = email.path("messageId").asText();
                zohoDelete(ZOHO_BASE + accountId + "/folders/8634380000000002014/messages/" + msgId);
            }
        }
    }

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
        zohoPost(ZOHO_BASE + accountId + "/messages", json);
    }

    private List<Message> getAllFromZoho() throws Exception {
        String url = ZOHO_BASE + accountId + "/messages/view?limit=200";
        JsonNode root = zohoGet(url);
        JsonNode data = root.path("data");

        Map<String, Message> byId = new LinkedHashMap<>();
        if (data.isArray()) {
            for (JsonNode email : data) {
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


    private Message decodeFromSubject(String subject, String summary) {
        try {
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


    private String reconstructBodyFromSummary(String summary) {
        // format Mailcontents id sender creationTime destination then convert to separator format
        int markerIdx = summary.indexOf("SD2526 ");
        if (markerIdx < 0) return "";

        String contents = summary.substring(0, markerIdx).trim();
        String meta = summary.substring(markerIdx + "SD2526 ".length());

        // Converts space-separated props back to newline format
        // check later if all that is necessary
        // "id=xxx sender=xxx creationTime=xxx destination=xxx"
        String metaNewlines = meta
                .replace(" sender=", "\nsender=")
                .replace(" creationTime=", "\ncreationTime=")
                .replace(" destination=", "\ndestination=");

        return contents + SEPARATOR + metaNewlines;
    }

    private void deleteFromZoho(String mid) throws Exception {
        String url = ZOHO_BASE + accountId + "/messages/view?limit=200";
        JsonNode root = zohoGet(url);
        JsonNode data = root.path("data");

        if (data.isArray()) {
            for (JsonNode email : data) {
                String subject = email.path("subject").asText("");
                if (subject.startsWith("SD2526|" + mid + "|")) {
                    String zohoMsgId = email.path("messageId").asText();
                    zohoDelete(ZOHO_BASE + accountId + "/folders/8634380000000002014/messages/" + zohoMsgId);
                }
            }
        }
    }


    // messages interfaces - check if there are no more problems with the methods

    @Override
    public Result<String> postMessage(String pwd, Message msg) {

        Log.info(() ->
                "postMessage : pwd=%s, msg=%s"
                        .formatted(pwd, msg));

        if (msg.getDestination() == null
                || msg.getDestination().isEmpty())

            return error(BAD_REQUEST);

        return getUser(msg.getSender(), pwd)
                .thenWith(user -> doPost(user, msg));
    }

    private Result<String> doPost(User sender, Message msg) {

        msg.setId(
                "%s+%04d".formatted(
                        THIS_DOMAIN,
                        counter.incrementAndGet()));

        msg.setSender(
                "%s <%s@%s>".formatted(
                        sender.getDisplayName(),
                        sender.getName(),
                        sender.getDomain()));

        var localAddresses =
                getLocalRecipientAddresses(msg);

        var remoteAddresses =
                getRemoteRecipientAddresses(msg);

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

    @Override
    public Result<Message> getInboxMessage(
            String name,
            String mid,
            String pwd) {

        Log.info(() ->
                "getInboxMessage : name=%s, mid=%s"
                        .formatted(name, mid));

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

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        Log.info(() -> "getAllInboxMessages : name=%s".formatted(name));

        return getUser(name, pwd).thenWith(u -> {
            try {
                String userAddress = name + "@" + THIS_DOMAIN;
                List<Message> all = getAllFromZoho();

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

                Log.info("Filtered for " + userAddress + ": " + ids);
                return ok(ids);

            } catch (Exception e) {
                e.printStackTrace();
                return error(INTERNAL_ERROR);
            }
        });
    }

    @Override
    public Result<List<String>> searchInbox(
            String name,
            String pwd,
            String query) {

        Log.info(() ->
                "searchInbox : name=%s, query=%s"
                        .formatted(name, query));

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

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        return getUser(name, pwd).thenWith(u -> {
            try {
                String userAddress = name + "@" + THIS_DOMAIN;
                String url = ZOHO_BASE + accountId + "/messages/view?limit=200";
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

                        // Apaga este email específico
                        zohoDelete(ZOHO_BASE + accountId + "/folders/8634380000000002014/messages/" + zohoMsgId);

                        // Se ainda há outros destinatários, guarda sem este
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

    @Override
    public Result<Void> deleteMessage(
            String name,
            String mid,
            String pwd) {

        Log.info(() ->
                "deleteMessage : name=%s, mid=%s"
                        .formatted(name, mid));

        return getUser(name, pwd).thenWith(u -> {

            try {

                Message found = null;

                for (Message m : getAllFromZoho())

                    if (mid.equals(m.getId())) {

                        found = m;

                        break;
                    }

                if (found == null)
                    return error(NOT_FOUND);

                if (!name.equals(
                        getName(found.senderAddress())))

                    return error(FORBIDDEN);

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

    // admin messages

    @Override
    public Result<Void> remotePostMessage(Message msg) {
        Log.info("remotePostMessage called: " + msg.getId());
        try {
            storeInZoho(msg);
            Log.info("storeInZoho SUCCESS for: " + msg.getId());
            return ok();
        } catch (Exception e) {
            e.printStackTrace();
            Log.warning("remotePostMessage failed: " + e.getMessage());
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> remoteDeleteMessage(String mid) {

        Log.info(() ->
                "remoteDeleteMessage : mid=%s"
                        .formatted(mid));

        try {

            deleteFromZoho(mid);

            return ok();

        } catch (Exception e) {

            e.printStackTrace();

            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {

        Log.info(() ->
                "remoteDeleteUserInbox : name=%s"
                        .formatted(name));

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

    private Result<User> getUser(
            String user,
            String pwd) {

        try {

            String addr = user;

            if (user.contains("<"))

                addr =
                        user.substring(
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

    private List<String> getLocalRecipientAddresses(Message msg) {

        return msg.getDestination()
                .stream()
                .filter(super::isLocalAddress)
                .toList();
    }

    private Set<String> getRemoteRecipientAddresses(Message msg) {

        return msg.getDestination()
                .stream()
                .filter(Predicate.not(super::isLocalAddress))
                .collect(Collectors.toSet());
    }
}