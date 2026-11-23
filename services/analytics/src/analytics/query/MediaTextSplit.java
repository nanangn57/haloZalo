package analytics.query;

public final class MediaTextSplit {
    private final long text;
    private final long media;

    public MediaTextSplit(long text, long media) {
        this.text = text;
        this.media = media;
    }

    public long getText() {
        return text;
    }

    public long getMedia() {
        return media;
    }
}
