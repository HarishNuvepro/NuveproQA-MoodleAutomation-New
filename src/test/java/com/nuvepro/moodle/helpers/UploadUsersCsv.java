package com.nuvepro.moodle.helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Creates scenario-scoped CSV fixtures for Moodle core Upload users. */
public final class UploadUsersCsv {
    private UploadUsersCsv() {}

    public static Path write(String name, List<String> headers, List<List<String>> rows) {
        Path dir = Path.of("target", "upload-users");
        try {
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            lines.add(headers.stream().map(UploadUsersCsv::escape).collect(Collectors.joining(",")));
            for (List<String> row : rows) {
                lines.add(row.stream().map(UploadUsersCsv::escape).collect(Collectors.joining(",")));
            }
            Path file = dir.resolve(name + ".csv");
            Files.write(file, lines, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Could not write Upload users CSV " + name, e);
        }
    }

    public static Path empty(String name) {
        Path dir = Path.of("target", "upload-users");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".csv");
            Files.writeString(file, "", StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> row(String... values) {
        return Arrays.asList(values);
    }

    private static String escape(String value) {
        String s = value == null ? "" : value;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
