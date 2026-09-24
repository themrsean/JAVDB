package service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class MediaTitleDeriverTest {
    private final MediaTitleDeriver deriver = new MediaTitleDeriver();

    @Test
    @DisplayName("Derives conservative titles from filenames")
    void derivesConservativeTitlesFromFilenames() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Sample Scene 01",
                        deriver.derive(Path.of("Sample_Scene_01.mp4"))
                ),
                () -> Assertions.assertEquals(
                        "Sample Scene Title 1080p",
                        deriver.derive(Path.of("Sample.Scene.Title.1080p.mkv"))
                ),
                () -> Assertions.assertEquals(
                        "Part-One Final",
                        deriver.derive(Path.of("Part-One_Final.mov"))
                ),
                () -> Assertions.assertEquals(
                        "Already Spaced",
                        deriver.derive(Path.of("Already Spaced.MP4"))
                ),
                () -> Assertions.assertEquals(
                        "NoExtension",
                        deriver.derive(Path.of("NoExtension"))
                ),
                () -> Assertions.assertEquals(
                        ".hidden-file",
                        deriver.derive(Path.of(".hidden-file"))
                ),
                () -> Assertions.assertEquals(
                        "Scène Été",
                        deriver.derive(Path.of("Scène_Été.mp4"))
                )
        );
    }

    @Test
    @DisplayName("Blank or invalid paths are rejected")
    void blankOrInvalidPathsAreRejected() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> deriver.derive(null)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> deriver.derive(Path.of(""))
                )
        );
    }
}
