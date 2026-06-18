package com.grpc.grpc.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.grpc.grpc.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Draw-and-save signature dialog (same UX as Action Form). */
public final class ReportSignatureHelper {

    public static final String TYPE_TECHNICIAN = "technician";
    public static final String TYPE_CUSTOMER = "customer";

    public interface Callback {
        void onSignatureSaved(Uri signatureUri, String signatureType);
    }

    private ReportSignatureHelper() {}

    public static void showCaptureDialog(AppCompatActivity activity, String signatureType, Callback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(
                TYPE_CUSTOMER.equals(signatureType)
                        ? R.string.report_signature_dialog_customer
                        : R.string.report_signature_dialog_technician));

        SignatureDrawingView signatureView = new SignatureDrawingView(activity);
        signatureView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400));

        LinearLayout dialogLayout = new LinearLayout(activity);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(20, 20, 20, 20);
        dialogLayout.addView(signatureView);

        TextView instructionText = new TextView(activity);
        instructionText.setText(R.string.report_signature_draw_hint);
        instructionText.setGravity(android.view.Gravity.CENTER);
        instructionText.setPadding(0, 10, 0, 0);
        dialogLayout.addView(instructionText);

        builder.setView(dialogLayout);
        builder.setPositiveButton(R.string.report_signature_save, (dialog, which) -> {
            if (signatureView.isEmpty()) {
                Toast.makeText(activity, R.string.report_signature_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = saveSignatureToFile(activity, signatureView.getSignatureBitmap(), signatureType);
            if (uri != null && callback != null) {
                callback.onSignatureSaved(uri, signatureType);
                Toast.makeText(activity,
                        TYPE_CUSTOMER.equals(signatureType)
                                ? R.string.report_signature_customer_saved
                                : R.string.report_signature_technician_saved,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, R.string.report_signature_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.report_signature_clear, (dialog, which) -> signatureView.clear());
        builder.setNeutralButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private static Uri saveSignatureToFile(Context context, Bitmap bitmap, String signatureType) {
        try {
            File signaturesFolder = new File(context.getExternalFilesDir(null), "SIGNATURES");
            if (!signaturesFolder.exists() && !signaturesFolder.mkdirs()) {
                return null;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = signatureType + "_signature_" + timestamp + ".png";
            File signatureFile = new File(signaturesFolder, filename);
            try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            return Uri.fromFile(signatureFile);
        } catch (IOException e) {
            return null;
        }
    }

    public static class SignatureDrawingView extends View {
        private final Path path = new Path();
        private final Paint paint = new Paint();
        private float lastX;
        private float lastY;
        private boolean hasSignature;

        public SignatureDrawingView(Context context) {
            super(context);
            paint.setColor(ContextCompat.getColor(context, R.color.signature_ink));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);
            paint.setAntiAlias(true);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            setBackgroundColor(ContextCompat.getColor(context, R.color.signature_bg));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path.moveTo(x, y);
                    lastX = x;
                    lastY = y;
                    hasSignature = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                    lastX = x;
                    lastY = y;
                    break;
                case MotionEvent.ACTION_UP:
                    path.lineTo(x, y);
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }

        public void clear() {
            path.reset();
            hasSignature = false;
            invalidate();
        }

        public boolean isEmpty() {
            return !hasSignature;
        }

        public Bitmap getSignatureBitmap() {
            Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            draw(canvas);
            return bitmap;
        }
    }
}
