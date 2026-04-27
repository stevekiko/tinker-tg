package com.tencent.tinker.lib.filepatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractFilePatch {

    public int patchFast(InputStream oldInputStream, InputStream diffInputStream, File newFile) throws IOException {
        return patchFast(oldInputStream, diffInputStream, newFile, false);
    }

    public abstract int patchFast(
            InputStream oldInputStream,
            InputStream diffInputStream,
            File newFile,
            boolean outputReadOnly
    ) throws IOException;

}
