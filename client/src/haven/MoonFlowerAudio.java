package haven;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Loads locally packaged, original MoonFlower music without using web resources. */
public final class MoonFlowerAudio {
    private MoonFlowerAudio() {
    }

    public static Audio.CS loop(String relativePath) {
        final File file = new File(Client.gameDir, relativePath);
        return(new Audio.Repeater() {
            private boolean warned;

            protected Audio.CS cons() {
                try {
                    return(Audio.PCMClip.fromwav(new BufferedInputStream(new FileInputStream(file))));
                } catch(IOException e) {
                    if(!warned) {
                        System.err.println("Could not play MoonFlower music " + file.getAbsolutePath() + ": " + e.getMessage());
                        warned = true;
                    }
                    return(null);
                }
            }
        });
    }
}
