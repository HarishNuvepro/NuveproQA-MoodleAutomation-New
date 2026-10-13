package com.nuvepro.moodle.helpers;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

/**
 * File lock shared across Maven processes on the same machine. In-JVM {@code ReentrantLock}s do not
 * serialize Upload users or admin login when Users, Upload users, and All courses run in separate
 * terminals against one Moodle site.
 */
public final class CrossProcessLock implements AutoCloseable {

    private final String name;
    private FileChannel channel;
    private FileLock lock;

    private CrossProcessLock(String name) {
        this.name = name;
    }

    public static CrossProcessLock named(String name) {
        return new CrossProcessLock(name);
    }

    public void lock(long timeout, TimeUnit unit) {
        if (disabled()) return;
        Path file = lockFile(name);
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        IOException lastIo = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Files.createDirectories(file.getParent());
                channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                lock = channel.tryLock();
                if (lock != null) {
                    return;
                }
                closeChannelQuietly();
            } catch (OverlappingFileLockException busy) {
                // Another thread in this JVM already holds a lock on this file — treat as contended.
                closeChannelQuietly();
            } catch (IOException e) {
                lastIo = e;
                closeChannelQuietly();
            }
            sleepQuietly(400);
        }
        if (lastIo != null) {
            throw new IllegalStateException("Cross-process lock I/O failed: " + name, lastIo);
        }
        throw new IllegalStateException("Cross-process lock timeout after " + unit.toMillis(timeout)
                + "ms: " + name);
    }

    public void unlock() {
        if (disabled()) return;
        try {
            if (lock != null) {
                lock.release();
                lock = null;
            }
        } catch (IOException ignored) {
            // best effort
        } finally {
            closeChannelQuietly();
        }
    }

    @Override
    public void close() {
        unlock();
    }

    private static Path lockFile(String name) {
        return Paths.get("target", "automation-locks", name + ".lock");
    }

    private static boolean disabled() {
        return Boolean.parseBoolean(System.getProperty("automation.crossProcessLocks.disable", "false"));
    }

    private void closeChannelQuietly() {
        if (channel == null) return;
        try {
            channel.close();
        } catch (IOException ignored) {
            // best effort
        }
        channel = null;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for cross-process lock", e);
        }
    }
}
