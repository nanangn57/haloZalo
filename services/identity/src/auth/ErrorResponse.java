package auth;

public final class ErrorResponse {
    private final Error error;

    public ErrorResponse(String code, String message) {
        this.error = new Error(code, message);
    }

    public Error getError() {
        return error;
    }

    public static final class Error {
        private final String code;
        private final String message;

        public Error(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}