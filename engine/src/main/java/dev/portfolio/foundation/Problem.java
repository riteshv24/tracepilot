package dev.portfolio.foundation;

/** Public, deliberately safe domain errors. Internal exception details never become API responses. */
public final class Problem extends RuntimeException {
    private final int status;
    private final String code;
    public Problem(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
    public int status() {
        return status;
    }
    public String code() {
        return code;
    }
    public static Problem missing(String resource) {
        return new Problem(404, "not_found", resource + " was not found");
    }
    public static Problem conflict(String detail) {
        return new Problem(409, "conflict", detail);
    }
    public static Problem invalid(String detail) {
        return new Problem(400, "invalid_request", detail);
    }
}
