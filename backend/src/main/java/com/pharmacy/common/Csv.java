package com.pharmacy.common;

import java.util.List;
import java.util.stream.Collectors;

public final class Csv {

    private Csv() {
    }

    public static String of(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(line(headers));
        for (List<String> row : rows) {
            builder.append(line(row));
        }
        return builder.toString();
    }

    private static String line(List<String> cells) {
        return cells.stream().map(Csv::cell).collect(Collectors.joining(",")) + "\n";
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
