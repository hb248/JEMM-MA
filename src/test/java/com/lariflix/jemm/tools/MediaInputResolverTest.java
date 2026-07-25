package com.lariflix.jemm.tools;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MediaInputResolverTest {

    private final MediaInputResolver resolver = new MediaInputResolver("http://host:8096/", "TOKEN");

    @Test
    public void resolvesLocalPathWhenFileExists() throws Exception {
        File temp = Files.createTempFile("jemm-probe", ".mp4").toFile();
        try {
            String resolved = resolver.resolve(temp.getAbsolutePath(), "abc123");
            assertEquals(temp.getAbsolutePath(), resolved);
        } finally {
            temp.delete();
        }
    }

    @Test
    public void fallsBackToDownloadUrlWhenPathMissing() {
        String resolved = resolver.resolve("Z:/does/not/exist.mkv", "abc123");
        assertEquals("http://host:8096/Items/abc123/Download?api_key=TOKEN", resolved);
    }

    @Test
    public void fallsBackToDownloadUrlWhenPathBlank() {
        String resolved = resolver.resolve("  ", "abc123");
        assertEquals("http://host:8096/Items/abc123/Download?api_key=TOKEN", resolved);
    }

    @Test
    public void addsTrailingSlashToBaseUrl() {
        MediaInputResolver noSlash = new MediaInputResolver("http://host:8096", "T");
        assertEquals("http://host:8096/Items/x/Download?api_key=T", noSlash.buildDownloadUrl("x"));
    }

    @Test
    public void returnsNullWhenItemIdMissing() {
        assertNull(resolver.buildDownloadUrl(null));
        assertNull(resolver.buildDownloadUrl(" "));
    }

    @Test
    public void buildsUrlWithoutTokenWhenBlank() {
        MediaInputResolver noToken = new MediaInputResolver("http://host/", "");
        assertEquals("http://host/Items/x/Download", noToken.buildDownloadUrl("x"));
    }
}
