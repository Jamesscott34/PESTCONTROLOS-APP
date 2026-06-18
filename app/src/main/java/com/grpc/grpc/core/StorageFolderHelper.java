package com.grpc.grpc.core;

import androidx.annotation.Nullable;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Storage listing helpers. {@link #discoverReportFolders} probes known ReportsYY / ReportsYYYY paths
 * (works when bucket root listing is restricted). {@link #discoverAllRootFolders} lists every
 * top-level prefix from the bucket root when rules allow it.
 */
public final class StorageFolderHelper {

    private StorageFolderHelper() {
    }

    public interface FolderListCallback {
        void onResult(List<String> folders);
    }

    /**
     * Lists all immediate child folder names under the storage bucket root (e.g. {@code contracts},
     * {@code Reports}, {@code Reports25}). Requires Storage rules that allow {@code list} on root.
     *
     * @param onRootListFailed called when root {@code listAll} fails (e.g. rules deny root list);
     *                         caller may fall back to {@link #discoverReportFolders}.
     */
    public static void discoverAllRootFolders(FolderListCallback onSuccess, Runnable onRootListFailed) {
        FirebaseStorage.getInstance().getReference().listAll()
                .addOnSuccessListener(listResult -> {
                    List<String> names = new ArrayList<>();
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        String n = prefix.getName();
                        if (n != null && !n.isEmpty()) {
                            names.add(n);
                        }
                    }
                    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
                    onSuccess.onResult(names);
                })
                .addOnFailureListener(e -> onRootListFailed.run());
    }

    /**
     * Parent folders for “upload to Firebase” pickers: list every top-level bucket folder when Storage rules
     * allow root {@code listAll}; otherwise fall back to probing known {@code ReportsYY} / {@code ReportsYYYY} paths.
     * Hides {@code contracts} (contract storage has its own flows; not for manual report dumps).
     */
    public static void discoverUploadParentFolders(FolderListCallback callback) {
        FolderListCallback wrapped = folders -> callback.onResult(filterOutContractsFromUploadList(folders));
        discoverAllRootFolders(wrapped, () -> discoverReportFolders(wrapped));
    }

    private static List<String> filterOutContractsFromUploadList(@Nullable List<String> folders) {
        if (folders == null || folders.isEmpty()) {
            return folders == null ? Collections.emptyList() : folders;
        }
        List<String> out = new ArrayList<>();
        for (String n : folders) {
            if (n == null) continue;
            String t = n.trim();
            if (t.isEmpty()) continue;
            if ("contracts".equalsIgnoreCase(t)) continue;
            out.add(n);
        }
        return out;
    }

    public static void discoverReportFolders(FolderListCallback callback) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        List<String> candidates = buildReportFolderCandidates();
        if (candidates.isEmpty()) {
            callback.onResult(Collections.emptyList());
            return;
        }

        Set<String> found = Collections.synchronizedSet(new LinkedHashSet<>());
        final int[] pending = {candidates.size()};
        for (String folderName : candidates) {
            StorageReference folderRef = storage.getReference().child(folderName);
            folderRef.listAll()
                    .addOnSuccessListener(result -> {
                        // listAll() can succeed with empty lists for paths with no objects; only count real folders.
                        if (listResultShowsStoredFolder(result)) {
                            found.add(folderName);
                        }
                        finishOne(found, pending, callback);
                    })
                    .addOnFailureListener(e -> finishOne(found, pending, callback));
        }
    }

    /**
     * True if Storage has at least one child prefix or file under this path (including {@code .keep}).
     */
    private static boolean listResultShowsStoredFolder(ListResult result) {
        if (result == null) {
            return false;
        }
        if (!result.getPrefixes().isEmpty()) {
            return true;
        }
        for (StorageReference item : result.getItems()) {
            if (item != null && item.getName() != null && !item.getName().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void finishOne(Set<String> found, int[] pending, FolderListCallback callback) {
        pending[0]--;
        if (pending[0] > 0) return;
        List<String> folders = new ArrayList<>(found);
        folders.sort((left, right) -> right.compareToIgnoreCase(left));
        callback.onResult(folders);
    }

    private static List<String> buildReportFolderCandidates() {
        List<String> out = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        for (int year = currentYear - 5; year <= currentYear + 2; year++) {
            out.add(String.format(Locale.getDefault(), "Reports%02d", year % 100));
            out.add("Reports" + year);
        }
        return out;
    }
}
