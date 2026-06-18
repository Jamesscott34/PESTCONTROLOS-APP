package com.grpc.grpc.pdfeditor;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.pdf.PdfRenderer;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.grpc.grpc.R;

/**
 * Renders each PDF page to a scaled bitmap with an interactive overlay.
 */
public class PdfEditorPageAdapter extends RecyclerView.Adapter<PdfEditorPageAdapter.Holder> {

    public interface PageUiCallbacks {
        PdfEditorOverlayView.EditMode getEditMode();

        int getInkColor();

        PdfEditorPageAnnotations annotationsForPage(int pageIndex);

        void onRequestTextForPage(int pageIndex, float nx, float ny);
    }

    private final PdfRenderer pdfRenderer;
    private final SparseArray<PdfEditorPageAnnotations> annotationIndex;
    private final PageUiCallbacks callbacks;
    private int maxWidthPx;

    public PdfEditorPageAdapter(PdfRenderer pdfRenderer,
                                SparseArray<PdfEditorPageAnnotations> annotationIndex,
                                PageUiCallbacks callbacks,
                                int maxWidthPx) {
        this.pdfRenderer = pdfRenderer;
        this.annotationIndex = annotationIndex;
        this.callbacks = callbacks;
        this.maxWidthPx = maxWidthPx;
    }

    public void setMaxWidthPx(int maxWidthPx) {
        this.maxWidthPx = maxWidthPx;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View root = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_editor_page, parent, false);
        return new Holder(root);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        int wParent = maxWidthPx > 0 ? maxWidthPx : parentWidth(holder);
        PdfRenderer.Page page = pdfRenderer.openPage(position);
        try {
            int pw = page.getWidth();
            int ph = page.getHeight();
            float scale = wParent / (float) pw;
            int dispW = wParent;
            int dispH = Math.max(1, Math.round(ph * scale));

            recycleBitmap(holder.imageView);
            Bitmap bmp = Bitmap.createBitmap(dispW, dispH, Bitmap.Config.ARGB_8888);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            holder.imageView.setImageBitmap(bmp);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) holder.imageView.getLayoutParams();
            lp.width = dispW;
            lp.height = dispH;
            holder.imageView.setLayoutParams(lp);

            FrameLayout.LayoutParams lpO = (FrameLayout.LayoutParams) holder.overlay.getLayoutParams();
            lpO.width = dispW;
            lpO.height = dispH;
            holder.overlay.setLayoutParams(lpO);

            ViewGroup.LayoutParams rootLp = holder.itemView.getLayoutParams();
            if (rootLp != null) {
                rootLp.height = dispH;
                rootLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                holder.itemView.setLayoutParams(rootLp);
            }

            holder.overlay.setEditMode(callbacks.getEditMode());
            holder.overlay.setInkColor(callbacks.getInkColor());
            PdfEditorPageAnnotations ann = callbacks.annotationsForPage(position);
            holder.overlay.setAnnotations(ann);
            final int pageIndex = position;
            holder.overlay.setTextRequestListener((nx, ny) -> callbacks.onRequestTextForPage(pageIndex, nx, ny));
        } finally {
            page.close();
        }
    }

    private static int parentWidth(Holder holder) {
        View p = (View) holder.itemView.getParent();
        if (p != null && p.getWidth() > 0) {
            return p.getWidth();
        }
        return holder.itemView.getResources().getDisplayMetrics().widthPixels;
    }

    private static void recycleBitmap(ImageView imageView) {
        android.graphics.drawable.Drawable d = imageView.getDrawable();
        if (d instanceof BitmapDrawable) {
            Bitmap old = ((BitmapDrawable) d).getBitmap();
            imageView.setImageDrawable(null);
            if (old != null && !old.isRecycled()) {
                old.recycle();
            }
        } else {
            imageView.setImageDrawable(null);
        }
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        recycleBitmap(holder.imageView);
        holder.overlay.setAnnotations(null);
        holder.overlay.setTextRequestListener(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return pdfRenderer.getPageCount();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final PdfEditorOverlayView overlay;

        Holder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.pdfEditorPageImage);
            overlay = itemView.findViewById(R.id.pdfEditorPageOverlay);
        }
    }
}
