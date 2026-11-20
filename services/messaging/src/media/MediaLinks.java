package media;

/**
 * Turns a mediaId into a link a browser can open without headers, as an img or video src.
 */
public interface MediaLinks {
    /**
     * A path relative to the messaging base URL: /media/{mediaId}?expires=...&sig=...
     */
    String url(String mediaId);

    /**
     * Whether expires and sig were made by {@link #url(String)} for this mediaId, and the link has not expired.
     */
    boolean verify(String mediaId, long expires, String sig);
}
