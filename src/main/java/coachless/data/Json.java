package coachless.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        if (text.startsWith("﻿")) text = text.substring(1);
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("Unexpected data after JSON");
        return v;
    }

    //safe readers
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    public static double num(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        return v instanceof Double ? (Double) v : fallback;
    }

    public static String str(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    // parser
    private Object value() {
        if (i >= s.length()) throw err("Unexpected end of file");
        switch (s.charAt(i)) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:  return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (at('}')) { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws(); expect(':'); ws();
            m.put(key, value());
            ws();
            if (at(',')) { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<>();
        i++; ws();
        if (at(']')) { i++; return a; }
        while (true) {
            ws();
            a.add(value());
            ws();
            if (at(',')) { i++; continue; }
            expect(']');
            return a;
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: sb.append(e);
            }
        }
        throw err("Unterminated string");
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw err("Unexpected character '" + s.charAt(i) + "'");
        return Double.valueOf(s.substring(start, i));
    }

    private Object literal(String word, Object result) {
        if (!s.startsWith(word, i)) throw err("Unexpected text");
        i += word.length();
        return result;
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private boolean at(char c) { return i < s.length() && s.charAt(i) == c; }
    private void expect(char c) { if (!at(c)) throw err("Expected '" + c + "'"); i++; }
    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException(msg + " at position " + i);
    }
}
