package com.grpc.grpc;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline storage for PDF template settings. Uses SharedPreferences + JSON for header blocks.
 */
public class PdfTemplateStorage {

    private static final String PREFS_NAME = "pdf_template_prefs";
    private static final String KEY_TEMPLATE_SELECTION = "template_selection";
    private static final String KEY_LOGO_PATH = "logo_path";
    private static final String KEY_WATERMARK_ENABLED = "watermark_enabled";
    private static final String KEY_WATERMARK_TYPE = "watermark_type";
    private static final String KEY_WATERMARK_TEXT = "watermark_text";
    private static final String KEY_WATERMARK_IMAGE_PATH = "watermark_image_path";
    private static final String KEY_HEADER_BLOCKS = "header_blocks";
    private static final String KEY_SAVED_TEMPLATES = "saved_templates";

    private final SharedPreferences prefs;

    public PdfTemplateStorage(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public PdfTemplateSettings load() {
        PdfTemplateSettings s = new PdfTemplateSettings();
        s.setTemplateSelection(prefs.getString(KEY_TEMPLATE_SELECTION, PdfTemplateSettings.GRPC));
        s.setLogoPath(prefs.getString(KEY_LOGO_PATH, null));
        s.setWatermarkEnabled(prefs.getBoolean(KEY_WATERMARK_ENABLED, false));
        s.setWatermarkType(prefs.getString(KEY_WATERMARK_TYPE, PdfTemplateSettings.WATERMARK_TEXT));
        s.setWatermarkText(prefs.getString(KEY_WATERMARK_TEXT, ""));
        s.setWatermarkImagePath(prefs.getString(KEY_WATERMARK_IMAGE_PATH, null));
        s.setHeaderBlocks(parseHeaderBlocks(prefs.getString(KEY_HEADER_BLOCKS, "[]")));
        return s;
    }

    public void save(PdfTemplateSettings s) {
        if (s == null) return;
        prefs.edit()
                .putString(KEY_TEMPLATE_SELECTION, s.getTemplateSelection())
                .putString(KEY_LOGO_PATH, s.getLogoPath())
                .putBoolean(KEY_WATERMARK_ENABLED, s.isWatermarkEnabled())
                .putString(KEY_WATERMARK_TYPE, s.getWatermarkType())
                .putString(KEY_WATERMARK_TEXT, s.getWatermarkText())
                .putString(KEY_WATERMARK_IMAGE_PATH, s.getWatermarkImagePath())
                .putString(KEY_HEADER_BLOCKS, serializeHeaderBlocks(s.getHeaderBlocks()))
                .apply();
    }

    private static String serializeHeaderBlocks(List<PdfTemplateSettings.HeaderBlock> blocks) {
        if (blocks == null) return "[]";
        try {
            JSONArray arr = new JSONArray();
            for (PdfTemplateSettings.HeaderBlock b : blocks) {
                JSONObject o = new JSONObject();
                o.put("blockType", b.getBlockType());
                o.put("textStyle", b.getTextStyle());
                o.put("text", b.getText());
                o.put("imagePath", b.getImagePath() != null ? b.getImagePath() : JSONObject.NULL);
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static List<PdfTemplateSettings.HeaderBlock> parseHeaderBlocks(String json) {
        List<PdfTemplateSettings.HeaderBlock> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                PdfTemplateSettings.HeaderBlock b = new PdfTemplateSettings.HeaderBlock();
                b.setBlockType(o.optString("blockType", PdfTemplateSettings.BLOCK_TEXT));
                b.setTextStyle(o.optString("textStyle", PdfTemplateSettings.STYLE_BODY));
                b.setText(o.optString("text", ""));
                b.setImagePath(o.has("imagePath") && !o.isNull("imagePath") ? o.optString("imagePath") : null);
                out.add(b);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Directory for template assets (logo, watermark image). Caller can copy files here. */
    public static File getTemplateDir(Context context) {
        File dir = new File(context.getFilesDir(), "pdf_template");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ---------- Named saved templates (per-user) ----------

    /** Normalized key for the current user (offline vs authenticated). */
    private static String userKey(String userName) {
        if (userName == null || userName.trim().isEmpty()) return "offline_user";
        String n = userName.trim().toLowerCase(Locale.US);
        if ("offline user".equals(n)) return "offline_user";
        return n.replaceAll("\\s+", "_");
    }

    private String keyForUser(String userName) {
        return KEY_SAVED_TEMPLATES + "_" + userKey(userName);
    }

    /** Load saved templates for the given user (offline or authenticated). */
    public List<SavedTemplate> loadSavedTemplates(String userName) {
        String key = keyForUser(userName);
        String json = prefs.getString(key, null);
        // Migrate legacy data: offline_user used to use KEY_SAVED_TEMPLATES
        if (json == null && "offline_user".equals(userKey(userName))) {
            json = prefs.getString(KEY_SAVED_TEMPLATES, "[]");
            if (json != null && !json.isEmpty() && !"[]".equals(json)) {
                prefs.edit().putString(key, json).remove(KEY_SAVED_TEMPLATES).apply();
            } else {
                json = "[]";
            }
        }
        if (json == null) json = "[]";
        return parseSavedTemplates(json);
    }

    public void saveSavedTemplates(String userName, List<SavedTemplate> list) {
        if (list == null) list = new ArrayList<>();
        prefs.edit().putString(keyForUser(userName), serializeSavedTemplates(list)).apply();
    }

    public void addSavedTemplate(String userName, SavedTemplate t) {
        if (t == null) return;
        if (userName == null) userName = "Offline User";
        if (t.getId() == null || t.getId().isEmpty()) t.setId(java.util.UUID.randomUUID().toString());
        List<SavedTemplate> list = loadSavedTemplates(userName);
        list.add(t);
        saveSavedTemplates(userName, list);
    }

    public SavedTemplate getSavedTemplateById(String userName, String id) {
        if (id == null) return null;
        if (userName == null) userName = "Offline User";
        for (SavedTemplate t : loadSavedTemplates(userName)) {
            if (id.equals(t.getId())) return t;
        }
        return null;
    }

    public void deleteSavedTemplate(String userName, String id) {
        if (userName == null) userName = "Offline User";
        List<SavedTemplate> list = loadSavedTemplates(userName);
        list.removeIf(t -> id.equals(t.getId()));
        saveSavedTemplates(userName, list);
    }

    private static String serializeSavedTemplates(List<SavedTemplate> list) {
        try {
            JSONArray arr = new JSONArray();
            for (SavedTemplate t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.getId());
                o.put("name", t.getName());
                o.put("logoPath", t.getLogoPath() != null ? t.getLogoPath() : JSONObject.NULL);
                o.put("watermarkEnabled", t.isWatermarkEnabled());
                o.put("watermarkType", t.getWatermarkType());
                o.put("watermarkText", t.getWatermarkText());
                o.put("watermarkImagePath", t.getWatermarkImagePath() != null ? t.getWatermarkImagePath() : JSONObject.NULL);
                o.put("headerBlocks", t.getHeaderBlocks() == null ? "[]" : serializeHeaderBlocks(t.getHeaderBlocks()));
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static List<SavedTemplate> parseSavedTemplates(String json) {
        List<SavedTemplate> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                SavedTemplate t = new SavedTemplate();
                t.setId(o.optString("id", ""));
                t.setName(o.optString("name", ""));
                t.setLogoPath(o.has("logoPath") && !o.isNull("logoPath") ? o.optString("logoPath") : null);
                t.setWatermarkEnabled(o.optBoolean("watermarkEnabled", false));
                t.setWatermarkType(o.optString("watermarkType", PdfTemplateSettings.WATERMARK_TEXT));
                t.setWatermarkText(o.optString("watermarkText", ""));
                t.setWatermarkImagePath(o.has("watermarkImagePath") && !o.isNull("watermarkImagePath") ? o.optString("watermarkImagePath") : null);
                String hb = o.optString("headerBlocks", "[]");
                t.setHeaderBlocks(parseHeaderBlocks(hb));
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
