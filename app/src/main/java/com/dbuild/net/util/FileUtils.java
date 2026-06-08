package com.dbuild.net.util;

import android.os.Environment;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.CRC32;

public class FileUtils {

    private FileUtils() {
    }

    public static boolean copyFile(File src, File dest) {
        if (src == null || dest == null) {
            return false;
        }
        if (!src.exists()) {
            return false;
        }
        File parentDir = dest.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return false;
            }
        }
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean moveFile(File src, File dest) {
        if (src == null || dest == null) {
            return false;
        }
        if (!src.exists()) {
            return false;
        }
        boolean renamed = src.renameTo(dest);
        if (renamed) {
            return true;
        }
        boolean copied = copyFile(src, dest);
        if (copied) {
            return src.delete();
        }
        return false;
    }

    public static boolean deleteRecursive(File file) {
        if (file == null) {
            return false;
        }
        if (!file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    public static String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    public static String getNameWithoutExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        String name = filename;
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = filename.substring(lastSlash + 1);
        }
        int lastBackSlash = name.lastIndexOf('\\');
        if (lastBackSlash >= 0) {
            name = name.substring(lastBackSlash + 1);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(0, lastDot);
        }
        return name;
    }

    public static long getFileSize(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        return file.length();
    }

    public static long getDirectorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += getDirectorySize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        if (gb < 1024) {
            return String.format("%.1f GB", gb);
        }
        double tb = gb / 1024.0;
        return String.format("%.1f TB", tb);
    }

    public static String readFileAsString(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toString("UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean writeStringToFile(String content, File file) {
        if (content == null || file == null) {
            return false;
        }
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return false;
            }
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static byte[] readBytes(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        long length = file.length();
        if (length > Integer.MAX_VALUE) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) length];
            int offset = 0;
            int remaining = data.length;
            while (remaining > 0) {
                int read = fis.read(data, offset, remaining);
                if (read == -1) {
                    break;
                }
                offset += read;
                remaining -= read;
            }
            if (offset < data.length) {
                byte[] trimmed = new byte[offset];
                System.arraycopy(data, 0, trimmed, 0, offset);
                return trimmed;
            }
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean writeBytes(byte[] data, File file) {
        if (data == null || file == null) {
            return false;
        }
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return false;
            }
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            fos.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean ensureDirectoryExists(File dir) {
        if (dir == null) {
            return false;
        }
        if (dir.exists()) {
            return dir.isDirectory();
        }
        return dir.mkdirs();
    }

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    public static String getMimeType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "application/octet-stream";
        }
        String extension = getExtension(filename);
        if (extension.isEmpty()) {
            return "application/octet-stream";
        }
        MimeTypeMap mimeMap = MimeTypeMap.getSingleton();
        String mime = mimeMap.getMimeTypeFromExtension(extension);
        if (mime != null) {
            return mime;
        }
        switch (extension) {
            case "glb":
                return "model/gltf-binary";
            case "gltf":
                return "model/gltf+json";
            case "obj":
                return "text/plain";
            case "mtl":
                return "text/plain";
            case "m3x":
                return "application/octet-stream";
            case "json":
                return "application/json";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "zip":
                return "application/zip";
            default:
                return "application/octet-stream";
        }
    }

    public static File createTempFile(String prefix, String suffix, File dir) {
        try {
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            if (dir != null) {
                return File.createTempFile(prefix, suffix, dir);
            } else {
                return File.createTempFile(prefix, suffix);
            }
        } catch (IOException e) {
            return null;
        }
    }

    public static String computeMD5(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                digest.update(buffer, 0, len);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static long computeCRC32(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            CRC32 crc32 = new CRC32();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                crc32.update(buffer, 0, len);
            }
            return crc32.getValue();
        } catch (IOException e) {
            return 0;
        }
    }

    public static boolean isFileExists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    public static boolean isDirectoryExists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File dir = new File(path);
        return dir.exists() && dir.isDirectory();
    }

    public static long getLastModified(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        return file.lastModified();
    }

    public static boolean renameFile(File src, String newName) {
        if (src == null || !src.exists()) {
            return false;
        }
        File dest = new File(src.getParent(), newName);
        return src.renameTo(dest);
    }

    public static int getFileCount(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (file.isFile()) {
                count++;
            } else if (file.isDirectory()) {
                count += getFileCount(file);
            }
        }
        return count;
    }

    public static String getHumanReadablePath(File file) {
        if (file == null) {
            return "";
        }
        return file.getAbsolutePath();
    }

    public static String joinPath(String... parts) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != File.separatorChar) {
                sb.append(File.separator);
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public static boolean clearDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return true;
        }
        boolean success = true;
        for (File file : files) {
            if (!deleteRecursive(file)) {
                success = false;
            }
        }
        return success;
    }

    public static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed";
        }
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
