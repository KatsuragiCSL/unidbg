package com.github.unidbg.file;

import com.github.unidbg.Emulator;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;
import com.github.unidbg.linux.file.Stdin;
import com.github.unidbg.linux.file.Stdout;
import com.github.unidbg.unix.IO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;

public abstract class BaseFileSystem implements FileSystem {

    private static final Log log = LogFactory.getLog(BaseFileSystem.class);

    protected final Emulator emulator;
    protected final File rootDir;

    public BaseFileSystem(Emulator emulator, File rootDir) {
        this.emulator = emulator;
        this.rootDir = rootDir;

        try {
            if (rootDir != null) {
                initialize(rootDir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("initialize file system failed", e);
        }
    }

    protected void initialize(File rootDir) throws IOException {
        FileUtils.forceMkdir(new File(rootDir, "tmp"));
    }

    @Override
    public FileIO open(String pathname, int oflags) {
        if (IO.STDIN.equals(pathname)) {
            return new Stdin(oflags);
        }

        if (rootDir == null) {
            return null;
        }

        if (IO.STDOUT.equals(pathname) || IO.STDERR.equals(pathname)) {
            try {
                File stdio = new File(rootDir, pathname + ".txt");
                if (!stdio.exists() && !stdio.createNewFile()) {
                    throw new IOException("create new file failed: " + stdio);
                }
                return new Stdout(oflags, stdio, pathname, IO.STDERR.equals(pathname), null); // TODO support callback
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        File file = new File(rootDir, pathname);
        return createFileIO(file, oflags, pathname);
    }

    private FileIO createFileIO(File file, int oflags, String path) {
        boolean directory = hasDirectory(oflags);
        if (file.isDirectory() && !directory) {
            return null;
        }
        if (file.isFile() && directory) {
            return null;
        }

        if (file.exists()) {
            return file.isDirectory() ? createDirectoryFileIO(file, oflags, path) : createSimpleFileIO(file, oflags, path);
        }

        if (!hasCreat(oflags) || !file.getParentFile().exists()) {
            return null;
        }

        try {
            if (directory) {
                if (!file.mkdir()) {
                    throw new IllegalStateException("mkdir failed: " + file);
                }
                return createDirectoryFileIO(file, oflags, path);
            } else {
                if (!file.createNewFile()) {
                    throw new IllegalStateException("createNewFile failed: " + file);
                }
                return createSimpleFileIO(file, oflags, path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("createNewFile failed: " + file, e);
        }
    }

    protected FileIO createSimpleFileIO(File file, int oflags, String path) {
        return new SimpleFileIO(oflags, file, path);
    }

    protected FileIO createDirectoryFileIO(File file, int oflags, String path) {
        return new DirectoryFileIO(oflags, path);
    }

    protected abstract boolean hasCreat(int oflags);
    protected abstract boolean hasDirectory(int oflags);
    protected abstract boolean hasAppend(int oflags);

    @Override
    public void unlink(String path) {
        if (rootDir == null) {
            log.info("unlink path=" + path);
        } else {
            File file = new File(rootDir, path);
            FileUtils.deleteQuietly(file);
            if (log.isDebugEnabled()) {
                log.debug("unlink path=" + path + ", file=" + file);
            }
        }
    }

    @Override
    public File getRootDir() {
        return rootDir;
    }

    @Override
    public File createWorkDir() {
        if (rootDir == null) {
            return null;
        } else {
            File workDir = new File(rootDir, "unidbg_work");
            if (!workDir.exists() && !workDir.mkdirs()) {
                throw new IllegalStateException("mkdirs failed: " + workDir);
            }
            return workDir;
        }
    }

}