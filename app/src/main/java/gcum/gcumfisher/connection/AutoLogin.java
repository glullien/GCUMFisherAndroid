package gcum.gcumfisher.connection;

public class AutoLogin {
    private final String code;
    private final String validTo;

    public AutoLogin(String code, String validTo) {
        this.code = code;
        this.validTo = validTo;
    }

    public String getCode() {
        return code;
    }

    public String getValidTo() {
        return validTo;
    }
}

