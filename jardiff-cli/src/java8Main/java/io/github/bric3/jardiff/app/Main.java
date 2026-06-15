package io.github.bric3.jardiff.app;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

public class Main {
    public static void main(String[] args) {
        try {
            Class<?> jardiffMainClass = Class.forName("io.github.bric3.jardiff.app.JardiffMain");
            jardiffMainClass.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (UnsupportedClassVersionError e) {
            System.err.println("Error: Incompatible Java version.");
            System.err.println("  Current Java version: " + System.getProperty("java.version"));
            System.err.println("  Required Java version: " + getRequiredJavaVersion() + " or higher");
            System.exit(1);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            System.err.println("Programming Error: Cannot run JardiffMain");
            System.exit(1);
        }
    }

    private static String getRequiredJavaVersion() {
        try (DataInputStream data = new DataInputStream(Main.class.getResourceAsStream("/io/github/bric3/jardiff/app/JardiffMain.class"))) {
            // Skip magic number (4 bytes) and minor version (2 bytes)
            data.skipBytes(6);
            int majorVersion = data.readUnsignedShort();

            // Convert major version to Java version
            // Java 8 = 52, Java 9 = 53, Java 10 = 54, Java 11 = 55, etc.
            int javaVersion = majorVersion - 44;
            return String.valueOf(javaVersion);
        } catch (NullPointerException | IOException e) {
            throw new IllegalStateException("Programming Error: Cannot run JardiffMain");
        }
    }
}
