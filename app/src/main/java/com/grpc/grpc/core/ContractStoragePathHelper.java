package com.grpc.grpc.core;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.grpc.grpc.BuildConfig;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers for contract-linked Firebase Storage paths, year folders,
 * and management-job folder browsing.
 */
public final class ContractStoragePathHelper {

    private static final String TAG = "ContractStoragePathHelper";
    private static final String MANAGEMENT_JOBS_PRIMARY = "management jobs";
    private static final String MANAGEMENT_JOBS_TYPO = "managment jobs";

    private static final Pattern TRAILING_4_DIGIT_YEAR = Pattern.compile("(20\\d{2})\\s*$");
    private static final Pattern TRAILING_2_DIGIT_YEAR = Pattern.compile("(?:^|[^\\d])(\\d{2})\\s*$");
    private static final Pattern DATE_YEAR = Pattern.compile("(20\\d{2})");
    private static final Pattern FOUR_DIGIT_YEAR_FOLDER = Pattern.compile("^\\d{4}$");

    public interface StringListCallback {
        void onResult(List<String> items);
    }

    public interface FolderContentsCallback {
        void onResult(List<String> subfolders, Map<String, String> files);
    }

    public interface RunnableCallback {
        void run();
    }

    public interface ErrorCallback {
        void onError(Exception error);
    }

    private ContractStoragePathHelper() {
    }

    /**
     * Returns a 4-digit year when the file name ends with a year token (e.g. {@code Report_2026.pdf}).
     */
    public static String extractYearFromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        String base = fileName.trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        Matcher m4 = TRAILING_4_DIGIT_YEAR.matcher(base);
        if (m4.find()) {
            return m4.group(1);
        }
        Matcher m2 = TRAILING_2_DIGIT_YEAR.matcher(base);
        if (m2.find()) {
            return "20" + m2.group(1);
        }
        Matcher anywhere = DATE_YEAR.matcher(base);
        String lastYear = null;
        while (anywhere.find()) {
            lastYear = anywhere.group(1);
        }
        return lastYear;
    }

    public static String resolveContractYearFolderPath(String folderPath, File pdfFile, String fallbackDateText) {
        String fileName = pdfFile != null ? pdfFile.getName() : "";
        return resolveContractYearFolderPath(folderPath, fileName, fallbackDateText);
    }

    /**
     * Appends a year segment to {@code contracts/{contractId}} when not already present.
     * Year priority: trailing year in file name, then date text, then current year.
     */
    public static String resolveContractYearFolderPath(String folderPath, String fileName, String fallbackDateText) {
        if (folderPath == null) {
            return null;
        }
        String normalized = folderPath.trim();
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("contracts/")) {
            return normalized;
        }
        String[] parts = normalized.split("/");
        if (parts.length >= 3 && FOUR_DIGIT_YEAR_FOLDER.matcher(parts[2]).matches()) {
            return normalized;
        }
        String year = resolveReportYear(fileName, fallbackDateText);
        return normalized + "/" + year;
    }

    private static String resolveReportYear(String fileName, String fallbackDateText) {
        String fromName = extractYearFromFileName(fileName);
        if (fromName != null && !fromName.isEmpty()) {
            return fromName;
        }
        String dateText = fallbackDateText != null ? fallbackDateText : "";
        Matcher d4 = DATE_YEAR.matcher(dateText);
        if (d4.find()) {
            return d4.group(1);
        }
        return new SimpleDateFormat("yyyy", Locale.getDefault()).format(new Date());
    }

    /**
     * Ensures {@code contracts/{contractId}/{year}/} exists by uploading a {@code .keep} placeholder when needed.
     * Firebase also creates the prefix on file upload; this makes empty year folders visible in listings.
     */
    public static void ensureYearFolderExists(
            String contractFolderPath,
            String year,
            RunnableCallback onReady,
            ErrorCallback onError
    ) {
        if (contractFolderPath == null || contractFolderPath.trim().isEmpty()
                || year == null || year.trim().isEmpty()) {
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        String folder = contractFolderPath.trim();
        String yearSegment = year.trim();
        StorageReference contractRef = FirebaseStorage.getInstance().getReference().child(folder);
        contractRef.listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        if (prefix != null && yearSegment.equals(prefix.getName())) {
                            if (onReady != null) {
                                onReady.run();
                            }
                            return;
                        }
                    }
                    contractRef.child(yearSegment + "/.keep")
                            .putBytes(new byte[0])
                            .addOnSuccessListener(task -> {
                                Log.d(TAG, "Created year folder placeholder: " + folder + "/" + yearSegment);
                                if (onReady != null) {
                                    onReady.run();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Year folder placeholder failed; continuing upload", e);
                                if (onReady != null) {
                                    onReady.run();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Could not verify year folder; continuing upload", e);
                    if (onReady != null) {
                        onReady.run();
                    }
                });
    }

    public static void listContractYearFolders(String contractId, StringListCallback callback) {
        if (!ContractReportSync.hasContractId(contractId)) {
            if (callback != null) {
                callback.onResult(Collections.emptyList());
            }
            return;
        }
        String contractFolder = ContractReportSync.buildContractStorageFolder(contractId);
        FirebaseStorage.getInstance()
                .getReference()
                .child(contractFolder)
                .listAll()
                .addOnSuccessListener(listResult -> {
                    LinkedHashSet<String> years = new LinkedHashSet<>();
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        if (prefix == null) {
                            continue;
                        }
                        String name = prefix.getName();
                        if (name != null && FOUR_DIGIT_YEAR_FOLDER.matcher(name).matches()) {
                            years.add(name);
                        }
                    }
                    List<String> sorted = new ArrayList<>(years);
                    Collections.sort(sorted, Collections.reverseOrder());
                    if (callback != null) {
                        callback.onResult(sorted);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to list contract year folders", e);
                    if (callback != null) {
                        callback.onResult(Collections.emptyList());
                    }
                });
    }

    public static void listFilesInFolder(String folderPath, FolderContentsCallback callback) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            if (callback != null) {
                callback.onResult(Collections.emptyList(), new LinkedHashMap<>());
            }
            return;
        }
        listFolderContents(folderPath.trim(), callback);
    }

    public static void listManagementJobFolders(StringListCallback callback) {
        resolveManagementJobsRootInternal(root -> listImmediateSubfolders(root, callback));
    }

    public static void resolveManagementJobsRoot(java.util.function.Consumer<String> callback) {
        resolveManagementJobsRootInternal(callback);
    }

    /**
     * Lists immediate subfolders and PDF files under a management job folder.
     */
    public static void listNestedManagementEntries(String folderPath, FolderContentsCallback callback) {
        listFolderContents(folderPath, callback);
    }

    /**
     * Collects all PDF storage paths under a folder, recursing into subfolders.
     */
    public static void listNestedManagementFiles(String folderPath, java.util.function.Consumer<Map<String, String>> callback) {
        LinkedHashMap<String, String> collected = new LinkedHashMap<>();
        collectNestedPdfs(folderPath, collected, () -> {
            if (callback != null) {
                callback.accept(collected);
            }
        });
    }

    private static void collectNestedPdfs(String folderPath, Map<String, String> out, Runnable onDone) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            onDone.run();
            return;
        }
        FirebaseStorage.getInstance()
                .getReference()
                .child(folderPath.trim())
                .listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference item : listResult.getItems()) {
                        if (item == null || item.getName() == null || ".keep".equalsIgnoreCase(item.getName())) {
                            continue;
                        }
                        if (item.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                            out.put(item.getPath(), item.getName());
                        }
                    }
                    List<StorageReference> prefixes = listResult.getPrefixes();
                    if (prefixes == null || prefixes.isEmpty()) {
                        onDone.run();
                        return;
                    }
                    final int[] pending = {prefixes.size()};
                    for (StorageReference prefix : prefixes) {
                        if (prefix == null || prefix.getName() == null) {
                            pending[0]--;
                            if (pending[0] <= 0) {
                                onDone.run();
                            }
                            continue;
                        }
                        collectNestedPdfs(folderPath + "/" + prefix.getName(), out, () -> {
                            pending[0]--;
                            if (pending[0] <= 0) {
                                onDone.run();
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> onDone.run());
    }

    private static void listFolderContents(String folderPath, FolderContentsCallback callback) {
        FirebaseStorage.getInstance()
                .getReference()
                .child(folderPath)
                .listAll()
                .addOnSuccessListener(listResult -> {
                    List<String> subfolders = new ArrayList<>();
                    Map<String, String> files = new LinkedHashMap<>();
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        if (prefix != null && prefix.getName() != null && !prefix.getName().isEmpty()) {
                            subfolders.add(prefix.getName());
                        }
                    }
                    for (StorageReference item : listResult.getItems()) {
                        if (item == null || item.getName() == null || ".keep".equalsIgnoreCase(item.getName())) {
                            continue;
                        }
                        files.put(item.getPath(), item.getName());
                    }
                    Collections.sort(subfolders, String.CASE_INSENSITIVE_ORDER);
                    if (callback != null) {
                        callback.onResult(subfolders, files);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to list folder: " + folderPath, e);
                    if (callback != null) {
                        callback.onResult(Collections.emptyList(), new LinkedHashMap<>());
                    }
                });
    }

    private static void listImmediateSubfolders(String folderPath, StringListCallback callback) {
        FirebaseStorage.getInstance()
                .getReference()
                .child(folderPath)
                .listAll()
                .addOnSuccessListener(listResult -> {
                    List<String> names = new ArrayList<>();
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        if (prefix != null && prefix.getName() != null && !prefix.getName().isEmpty()) {
                            names.add(prefix.getName());
                        }
                    }
                    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
                    if (callback != null) {
                        callback.onResult(names);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to list management job folders", e);
                    if (callback != null) {
                        callback.onResult(Collections.emptyList());
                    }
                });
    }

    private static void resolveManagementJobsRootInternal(java.util.function.Consumer<String> callback) {
        FirebaseStorage.getInstance().getReference().listAll()
                .addOnSuccessListener(listResult -> {
                    List<String> roots = new ArrayList<>();
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        if (prefix != null && prefix.getName() != null) {
                            roots.add(prefix.getName().trim());
                        }
                    }
                    for (String root : roots) {
                        if (MANAGEMENT_JOBS_PRIMARY.equalsIgnoreCase(root)) {
                            callback.accept(root);
                            return;
                        }
                    }
                    for (String root : roots) {
                        if (MANAGEMENT_JOBS_TYPO.equalsIgnoreCase(root)) {
                            callback.accept(root);
                            return;
                        }
                    }
                    callback.accept(MANAGEMENT_JOBS_PRIMARY);
                })
                .addOnFailureListener(e -> callback.accept(MANAGEMENT_JOBS_PRIMARY));
    }

    public static String preferredManagementJobsRootName() {
        return "grpc".equalsIgnoreCase(BuildConfig.FLAVOR)
                ? MANAGEMENT_JOBS_TYPO
                : MANAGEMENT_JOBS_PRIMARY;
    }

    public static String yearFromContractFolderPath(String folderPath) {
        if (folderPath == null) {
            return null;
        }
        String[] parts = folderPath.split("/");
        if (parts.length >= 3 && FOUR_DIGIT_YEAR_FOLDER.matcher(parts[2]).matches()) {
            return parts[2];
        }
        return null;
    }

    public interface FileSearchCallback {
        void onResult(List<FileSearchHit> hits);
    }

    public static final class FileSearchHit {
        public final String storagePath;
        public final String fileName;
        public final String folderLabel;

        public FileSearchHit(String storagePath, String fileName, String folderLabel) {
            this.storagePath = storagePath != null ? storagePath : "";
            this.fileName = fileName != null ? fileName : "";
            this.folderLabel = folderLabel != null ? folderLabel : "";
        }
    }

    /**
     * Recursively searches files under one or more bucket folders.
     * Matches case-insensitively and treats spaces, underscores, and hyphens as equivalent
     * (e.g. query {@code fired up} matches {@code fired_up.pdf}).
     */
    public static void searchFilesByName(
            List<String> rootPaths,
            String query,
            int maxResults,
            FileSearchCallback callback
    ) {
        if (rootPaths == null || rootPaths.isEmpty() || query == null || query.trim().isEmpty()) {
            if (callback != null) {
                callback.onResult(Collections.emptyList());
            }
            return;
        }
        final String queryLower = query.trim().toLowerCase(Locale.ROOT);
        final LinkedHashMap<String, FileSearchHit> hits = new LinkedHashMap<>();
        final int[] pendingRoots = {rootPaths.size()};
        Runnable finish = () -> {
            pendingRoots[0]--;
            if (pendingRoots[0] > 0) {
                return;
            }
            if (callback != null) {
                callback.onResult(new ArrayList<>(hits.values()));
            }
        };
        for (String root : rootPaths) {
            if (root == null || root.trim().isEmpty()) {
                finish.run();
                continue;
            }
            collectSearchHits(root.trim(), queryLower, hits, maxResults, finish);
        }
    }

    private static void collectSearchHits(
            String folderPath,
            String queryLower,
            Map<String, FileSearchHit> hits,
            int maxResults,
            Runnable onComplete
    ) {
        if (hits.size() >= maxResults) {
            onComplete.run();
            return;
        }
        FirebaseStorage.getInstance()
                .getReference()
                .child(folderPath)
                .listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference item : listResult.getItems()) {
                        if (hits.size() >= maxResults) {
                            break;
                        }
                        if (item == null || item.getName() == null || ".keep".equalsIgnoreCase(item.getName())) {
                            continue;
                        }
                        String fileName = item.getName();
                        if (fileNameMatchesSearch(fileName, queryLower)) {
                            hits.put(item.getPath(), new FileSearchHit(item.getPath(), fileName, folderPath));
                        }
                    }
                    List<StorageReference> prefixes = listResult.getPrefixes();
                    if (prefixes == null || prefixes.isEmpty() || hits.size() >= maxResults) {
                        onComplete.run();
                        return;
                    }
                    final int[] pending = {prefixes.size()};
                    for (StorageReference prefix : prefixes) {
                        if (prefix == null || prefix.getName() == null) {
                            pending[0]--;
                            if (pending[0] <= 0) {
                                onComplete.run();
                            }
                            continue;
                        }
                        collectSearchHits(
                                folderPath + "/" + prefix.getName(),
                                queryLower,
                                hits,
                                maxResults,
                                () -> {
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        onComplete.run();
                                    }
                                }
                        );
                    }
                })
                .addOnFailureListener(e -> onComplete.run());
    }

    /** Normalizes for search: lowercase and strip spaces, underscores, hyphens. */
    public static String normalizeSearchText(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "");
    }

    /**
     * Returns true when {@code fileName} matches {@code query} using flexible token matching.
     * Spaces, underscores, and hyphens are treated as word separators in both strings.
     */
    public static boolean fileNameMatchesSearch(@Nullable String fileName, @Nullable String query) {
        if (fileName == null || query == null) {
            return false;
        }
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            return false;
        }
        String normalizedFile = normalizeSearchText(fileName);
        String normalizedQuery = normalizeSearchText(trimmedQuery);
        if (!normalizedQuery.isEmpty() && normalizedFile.contains(normalizedQuery)) {
            return true;
        }
        String[] tokens = trimmedQuery.toLowerCase(Locale.ROOT).split("[\\s_\\-]+");
        if (tokens.length <= 1) {
            return normalizedFile.contains(normalizedQuery);
        }
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (!normalizedFile.contains(normalizeSearchText(token))) {
                return false;
            }
        }
        return true;
    }
}

