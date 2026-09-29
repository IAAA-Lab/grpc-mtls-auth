package dev.example.grpc.hello;

/**
 * Tokens bearer de la demo. No son secretos; el log de Compose muestra si se aceptan o se rechazan.
 */
public final class DemoTokens {

    public static final String GREETER = "greeter-token";
    public static final String OBSERVER = "observer-token";
    public static final String INVALID = "not-a-token";

    public static final String ROLE_GREETER = "ROLE_GREETER";
    public static final String ROLE_OBSERVER = "ROLE_OBSERVER";

    private DemoTokens() {
    }
}
