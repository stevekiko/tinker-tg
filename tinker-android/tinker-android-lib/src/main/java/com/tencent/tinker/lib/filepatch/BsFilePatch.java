package com.tencent.tinker.lib.filepatch;

import com.tencent.tinker.bsdiff.BSPatch;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings("IOStreamConstructor")
public class BsFilePatch extends AbstractFilePatch{

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public int patchFast(InputStream oldInputStream, InputStream diffInputStream, File newFile, boolean outputReadOnly)
        throws IOException {
        if (!outputReadOnly) {
            return BSPatch.patchFast(oldInputStream, diffInputStream, newFile);
        }
        if (oldInputStream == null) {
            return BSPatch.RETURN_OLD_FILE_ERR;
        }
        if (newFile == null) {
            return BSPatch.RETURN_NEW_FILE_ERR;
        }
        if (diffInputStream == null) {
            return BSPatch.RETURN_DIFF_FILE_ERR;
        }
        byte[] newBytes = BSPatch.patchFast(oldInputStream, diffInputStream);
        if (newBytes == null) {
            return BSPatch.RETURN_OLD_FILE_ERR;
        }
        OutputStream newOutputStream = null;
        try {
            newOutputStream = new FileOutputStream(newFile);
            newFile.setReadOnly();
            newOutputStream.write(newBytes);
        } finally {
            IOHelper.closeQuietly(newOutputStream);
        }
        return BSPatch.RETURN_SUCCESS;
    }
}
