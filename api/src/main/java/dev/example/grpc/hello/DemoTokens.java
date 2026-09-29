package dev.example.grpc.hello;

/**
 * Demo bearer tokens. They are not secrets; the Compose log is meant to show them being accepted or rejected.
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
