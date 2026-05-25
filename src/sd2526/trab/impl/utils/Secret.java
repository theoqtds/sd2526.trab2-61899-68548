package sd2526.trab.impl.utils;

public class Secret {
    private static Secret instance;
    private final String secret;

    private Secret(String secret) {
        this.secret = secret;
    }

    public static void init(String secret) {
        if (instance == null) {
            instance = new Secret(secret);
        }
    }

    public static Secret getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Secret has not been initialized");
        }
        return instance;
    }

    public String getSecret() {
        return this.secret;
    }
}
