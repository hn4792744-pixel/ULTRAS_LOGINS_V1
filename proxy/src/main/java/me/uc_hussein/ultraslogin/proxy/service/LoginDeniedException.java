package me.uc_hussein.ultraslogin.proxy.service;

/** Thrown inside the login pipeline to deny a connection with a localized reason. */
public final class LoginDeniedException extends RuntimeException {
    private final String messageKey;
    private final transient Placeholders placeholders;

    public LoginDeniedException(String messageKey, Placeholders placeholders) {
        super(messageKey, null, false, false);
        this.messageKey = messageKey;
        this.placeholders = placeholders;
    }

    public String messageKey() {
        return messageKey;
    }

    public Placeholders placeholders() {
        return placeholders;
    }
}
