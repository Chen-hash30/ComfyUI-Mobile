package com.example.demo.manager;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HistoryStore {

    private static final String PREF_NAME = "comfy_history";
    private static final String PREF_KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 100;

    private HistoryStore() {
    }

    public static final class HistoryEntry {
        public final String url;
        public final String localPath;
        public final String prompt;
        public final JsonObject params;
        public final String time;

        public HistoryEntry(String url, String localPath, String prompt, JsonObject params, String time) {
            this.url = url;
            this.localPath = localPath;
            this.prompt = prompt;
            this.params = params;
            this.time = time;
        }
    }

    public static final class HistoryGroup {
        public final String prompt;
        public final String time;
        public final List<HistoryEntry> entries;

        public HistoryGroup(String prompt, String time, List<HistoryEntry> entries) {
            this.prompt = prompt;
            this.time = time;
            this.entries = entries;
        }
    }

    public static void saveHistory(Context context, String url, String localPath, String prompt, JsonObject params) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JsonArray current = parseArray(prefs.getString(PREF_KEY_ITEMS, "[]"));
        JsonArray next = new JsonArray();

        JsonObject latest = new JsonObject();
        latest.addProperty("url", url);
        latest.addProperty("localPath", localPath != null ? localPath : "");
        latest.addProperty("prompt", prompt);
        if (params != null) {
            latest.add("params", params);
        }
        latest.addProperty("time", new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        next.add(latest);

        for (int i = 0; i < current.size() && next.size() < MAX_ITEMS; i++) {
            JsonObject item = current.get(i).getAsJsonObject();
            if (item.has("url") && !url.equals(item.get("url").getAsString())) {
                next.add(item);
            }
        }

        prefs.edit().putString(PREF_KEY_ITEMS, next.toString()).apply();
    }

    public static List<HistoryEntry> loadEntries(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JsonArray array = parseArray(prefs.getString(PREF_KEY_ITEMS, "[]"));
        List<HistoryEntry> entries = new ArrayList<>();

        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String url = object.has("url") ? object.get("url").getAsString() : "";
            String localPath = object.has("localPath") ? object.get("localPath").getAsString() : "";
            String prompt = object.has("prompt") ? object.get("prompt").getAsString() : "";
            String time = object.has("time") ? object.get("time").getAsString() : "";
            JsonObject params = object.has("params") && object.get("params").isJsonObject()
                    ? object.getAsJsonObject("params")
                    : null;
            entries.add(new HistoryEntry(url, localPath, prompt, params, time));
        }

        return entries;
    }

    public static List<HistoryGroup> loadGroups(Context context) {
        List<HistoryEntry> entries = loadEntries(context);
        Map<String, List<HistoryEntry>> grouped = new LinkedHashMap<>();

        for (HistoryEntry entry : entries) {
            if (!grouped.containsKey(entry.prompt)) {
                grouped.put(entry.prompt, new ArrayList<>());
            }
            grouped.get(entry.prompt).add(entry);
        }

        List<HistoryGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<HistoryEntry>> entry : grouped.entrySet()) {
            List<HistoryEntry> groupEntries = entry.getValue();
            String time = groupEntries.isEmpty() ? "" : groupEntries.get(0).time;
            groups.add(new HistoryGroup(entry.getKey(), time, groupEntries));
        }
        return groups;
    }

    public static void deleteByPrompt(Context context, String prompt) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JsonArray array = parseArray(prefs.getString(PREF_KEY_ITEMS, "[]"));
        JsonArray filtered = new JsonArray();

        for (int i = 0; i < array.size(); i++) {
            JsonObject item = array.get(i).getAsJsonObject();
            if (!item.has("prompt") || !prompt.equals(item.get("prompt").getAsString())) {
                filtered.add(item);
            }
        }

        prefs.edit().putString(PREF_KEY_ITEMS, filtered.toString()).apply();
    }

    private static JsonArray parseArray(String value) {
        try {
            JsonElement element = JsonParser.parseString(value == null ? "[]" : value);
            if (element != null && element.isJsonArray()) {
                return element.getAsJsonArray();
            }
        } catch (Exception ignored) {
        }
        return new JsonArray();
    }
}