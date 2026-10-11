package com.theruzil.finance.importing;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

class CsvParser {

    List<List<String>> parse(Reader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            content.append(buffer, 0, read);
        }
        return split(content.toString());
    }

    private List<List<String>> split(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int index = 0;
        int length = text.length();
        while (index < length) {
            char current = text.charAt(index);
            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < length && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index += 2;
                        continue;
                    }
                    inQuotes = false;
                    index++;
                    continue;
                }
                field.append(current);
                index++;
                continue;
            }
            if (current == '"') {
                inQuotes = true;
                index++;
                continue;
            }
            if (current == ',') {
                row.add(trim(field));
                index++;
                continue;
            }
            if (current == '\r' || current == '\n') {
                row.add(trim(field));
                rows.add(row);
                row = new ArrayList<>();
                index += current == '\r' && index + 1 < length && text.charAt(index + 1) == '\n' ? 2 : 1;
                continue;
            }
            field.append(current);
            index++;
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(trim(field));
            rows.add(row);
        }
        return rows;
    }

    private String trim(StringBuilder field) {
        String value = field.toString()
                .replaceAll("^[\\s\\u00A0\\u202F\\u2009]+", "")
                .replaceAll("[\\s\\u00A0\\u202F\\u2009]+$", "");
        field.setLength(0);
        return value;
    }
}
