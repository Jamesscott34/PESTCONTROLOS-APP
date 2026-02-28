package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline PDF template settings: logo, watermark, header blocks.
 * Stored via PdfTemplateStorage (SharedPreferences + files in pdf_template dir).
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class PdfTemplateSettingsActivity extends AppCompatActivity {

    private static final int MAX_HEADER_BLOCKS = 20;

    private PdfTemplateStorage storage;
    private PdfTemplateSettings settings;
    private String userName;

    private TextView logoPathLabel;
    private EditText watermarkTextInput;
    private EditText footerTextInput;
    private Button chooseWatermarkImageButton;
    private TextView watermarkImagePathLabel;
    private LinearLayout headerBlocksContainer;

    private int pendingHeaderBlockRowIndex = -1;
    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                if (pendingHeaderBlockRowIndex >= 0 && pendingHeaderBlockRowIndex < headerBlocksContainer.getChildCount()) {
                    copyToTemplateDirAndSetHeaderBlockImage(uri, pendingHeaderBlockRowIndex);
                    pendingHeaderBlockRowIndex = -1;
                }
            });

    private final ActivityResultLauncher<Intent> pickLogoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                copyToTemplateDirAndSetLogo(uri);
            });

    private final ActivityResultLauncher<Intent> pickWatermarkImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                copyToTemplateDirAndSetWatermarkImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_template_settings);

        // Offline app: custom templates require signing up for demo or downloading the latest offline app
        if (BuildConfig.IS_OFFLINE) {
            OfflineTrialHelper.openWebsiteAndFinish(this, getString(R.string.main_website_url), getString(R.string.offline_custom_template_blocked));
            return;
        }

        // Demo/other: trial redirect after N days
        if (OfflineTrialHelper.openWebsiteIfExpired(this, getString(R.string.main_website_url), getString(R.string.offline_trial_redirect_message))) {
            return;
        }

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) userName = "Offline User";
        storage = new PdfTemplateStorage(this);
        // Always start with empty form so user is creating a new template; "My Template" in Create Report uses storage from last Save template settings
        settings = new PdfTemplateSettings();

        logoPathLabel = findViewById(R.id.logoPathLabel);
        watermarkTextInput = findViewById(R.id.watermarkTextInput);
        footerTextInput = findViewById(R.id.footerTextInput);
        chooseWatermarkImageButton = findViewById(R.id.chooseWatermarkImageButton);
        watermarkImagePathLabel = findViewById(R.id.watermarkImagePathLabel);
        headerBlocksContainer = findViewById(R.id.headerBlocksContainer);

        android.widget.EditText mainHeaderInput = findViewById(R.id.mainHeaderInput);
        if (mainHeaderInput != null) mainHeaderInput.setText(settings.getMainHeaderText());
        setupMainHeaderColorSpinner();
        setupHeaderSizeSpinner();
        setupBodyTextSizeSpinner();
        if (footerTextInput != null) footerTextInput.setText(settings.getFooterText() != null ? settings.getFooterText() : "");
        bindLogo();
        bindWatermark();
        bindHeaderBlocks();
        setupWatermarkTypeToggle();
        findViewById(R.id.chooseLogoButton).setOnClickListener(v -> openImagePicker(pickLogoLauncher));
        findViewById(R.id.useDefaultLogoButton).setOnClickListener(v -> {
            settings.setLogoPath(PdfTemplateSettings.USE_DEFAULT_LOGO);
            bindLogo();
            Toast.makeText(this, "Using default logo (" + TenantBranding.companyAbbrev(this) + ").", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.chooseWatermarkImageButton).setOnClickListener(v -> openImagePicker(pickWatermarkImageLauncher));
        findViewById(R.id.addHeaderBlockButton).setOnClickListener(v -> addHeaderBlockRow());
        findViewById(R.id.saveTemplateButton).setOnClickListener(v -> saveSettingsAndClear());
        findViewById(R.id.viewTemplatesButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, ViewTemplatesActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });
    }

    private static final String[] MAIN_HEADER_COLOR_NAMES = {"Blue", "Black", "Dark Gray", "Red", "Green"};
    private static final String[] MAIN_HEADER_COLOR_HEX = {"#0000FF", "#000000", "#333333", "#CC0000", "#006600"};
    private static final String[] HEADER_SIZE_NAMES = {"Default", "Bigger", "Smaller"};
    private static final String[] HEADER_SIZE_VALUES = {PdfTemplateSettings.HEADER_SIZE_DEFAULT, PdfTemplateSettings.HEADER_SIZE_BIGGER, PdfTemplateSettings.HEADER_SIZE_SMALLER};
    private static final String[] BODY_TEXT_SIZE_NAMES = {"Default (12pt)", "8pt", "10pt", "12pt", "14pt"};
    private static final String[] BODY_TEXT_SIZE_VALUES = {PdfTemplateSettings.BODY_TEXT_SIZE_DEFAULT, "8", "10", "12", "14"};

    private void setupMainHeaderColorSpinner() {
        Spinner spinner = findViewById(R.id.mainHeaderColorSpinner);
        if (spinner == null) return;
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MAIN_HEADER_COLOR_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String savedHex = settings.getMainHeaderColorHex();
        for (int i = 0; i < MAIN_HEADER_COLOR_HEX.length; i++) {
            if (MAIN_HEADER_COLOR_HEX[i].equalsIgnoreCase(savedHex)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void setupHeaderSizeSpinner() {
        Spinner spinner = findViewById(R.id.headerSizeSpinner);
        if (spinner == null) return;
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, HEADER_SIZE_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String saved = settings.getHeaderSize();
        for (int i = 0; i < HEADER_SIZE_VALUES.length; i++) {
            if (HEADER_SIZE_VALUES[i].equals(saved)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void setupBodyTextSizeSpinner() {
        Spinner spinner = findViewById(R.id.bodyTextSizeSpinner);
        if (spinner == null) return;
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, BODY_TEXT_SIZE_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String saved = settings.getBodyTextSize();
        if (saved == null) saved = PdfTemplateSettings.BODY_TEXT_SIZE_DEFAULT;
        if (PdfTemplateSettings.BODY_TEXT_SIZE_SMALLER.equals(saved)) saved = "10";
        if (PdfTemplateSettings.BODY_TEXT_SIZE_BIGGER.equals(saved)) saved = "14";
        for (int i = 0; i < BODY_TEXT_SIZE_VALUES.length; i++) {
            if (BODY_TEXT_SIZE_VALUES[i].equals(saved)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void bindLogo() {
        String path = settings.getLogoPath();
        if (path != null && !path.isEmpty()) {
            logoPathLabel.setVisibility(android.view.View.VISIBLE);
            if (PdfTemplateSettings.USE_DEFAULT_LOGO.equals(path)) {
                logoPathLabel.setText("Default logo (" + TenantBranding.companyAbbrev(this) + ")");
            } else {
                logoPathLabel.setText(new File(path).getName());
            }
        } else {
            logoPathLabel.setVisibility(android.view.View.GONE);
        }
    }

    private void bindWatermark() {
        ((CheckBox) findViewById(R.id.watermarkEnabled)).setChecked(settings.isWatermarkEnabled());
        watermarkTextInput.setText(settings.getWatermarkText());
        if (PdfTemplateSettings.WATERMARK_IMAGE.equals(settings.getWatermarkType())) {
            ((RadioButton) findViewById(R.id.watermarkTypeImage)).setChecked(true);
            chooseWatermarkImageButton.setVisibility(android.view.View.VISIBLE);
            if (settings.getWatermarkImagePath() != null) {
                watermarkImagePathLabel.setText(new File(settings.getWatermarkImagePath()).getName());
                watermarkImagePathLabel.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            ((RadioButton) findViewById(R.id.watermarkTypeText)).setChecked(true);
            chooseWatermarkImageButton.setVisibility(android.view.View.GONE);
        }
    }

    private void setupWatermarkTypeToggle() {
        RadioGroup wg = findViewById(R.id.watermarkTypeGroup);
        wg.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isImage = checkedId == R.id.watermarkTypeImage;
            chooseWatermarkImageButton.setVisibility(isImage ? android.view.View.VISIBLE : android.view.View.GONE);
            watermarkTextInput.setVisibility(isImage ? android.view.View.GONE : android.view.View.VISIBLE);
        });
        boolean isImage = PdfTemplateSettings.WATERMARK_IMAGE.equals(settings.getWatermarkType());
        chooseWatermarkImageButton.setVisibility(isImage ? android.view.View.VISIBLE : android.view.View.GONE);
        watermarkTextInput.setVisibility(isImage ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void bindHeaderBlocks() {
        headerBlocksContainer.removeAllViews();
        for (int i = 0; i < settings.getHeaderBlocks().size(); i++) {
            addHeaderBlockRow(settings.getHeaderBlocks().get(i), i);
        }
    }

    private void addHeaderBlockRow() {
        addHeaderBlockRow(null, headerBlocksContainer.getChildCount());
    }

    private void addHeaderBlockRow(PdfTemplateSettings.HeaderBlock existing, int index) {
        if (headerBlocksContainer.getChildCount() >= MAX_HEADER_BLOCKS) {
            Toast.makeText(this, "Max " + MAX_HEADER_BLOCKS + " header blocks.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, 8, 0, 8);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);

        RadioGroup typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbText = new RadioButton(this);
        rbText.setId(android.view.View.generateViewId());
        rbText.setText("Text");
        RadioButton rbImage = new RadioButton(this);
        rbImage.setId(android.view.View.generateViewId());
        rbImage.setText("Image");
        typeGroup.addView(rbText);
        typeGroup.addView(rbImage);

        Spinner styleSpinner = new Spinner(this);
        styleSpinner.setId(android.view.View.generateViewId());
        android.widget.ArrayAdapter<String> styleAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{PdfTemplateSettings.STYLE_H1, PdfTemplateSettings.STYLE_H2, PdfTemplateSettings.STYLE_BODY});
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        styleSpinner.setAdapter(styleAdapter);

        EditText textInput = new EditText(this);
        textInput.setHint("Header text");
        textInput.setPadding(16, 12, 16, 12);
        textInput.setMinLines(3);
        textInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textInput.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        textInput.setLayoutParams(textParams);

        Button chooseImgBtn = new Button(this);
        chooseImgBtn.setText("Choose image");
        chooseImgBtn.setVisibility(android.view.View.GONE);

        chooseImgBtn.setOnClickListener(v -> {
            pendingHeaderBlockRowIndex = headerBlocksContainer.indexOfChild(row);
            openImagePicker(pickImageLauncher);
        });

        Button removeBtn = new Button(this);
        removeBtn.setText("Remove");
        removeBtn.setOnClickListener(v -> {
            headerBlocksContainer.removeView(row);
        });

        BlockRowHolder holder = new BlockRowHolder(rbText, rbImage, styleSpinner, textInput, chooseImgBtn);
        if (existing != null) {
            if (PdfTemplateSettings.BLOCK_IMAGE.equals(existing.getBlockType())) {
                rbImage.setChecked(true);
                chooseImgBtn.setVisibility(android.view.View.VISIBLE);
                textInput.setVisibility(android.view.View.GONE);
                styleSpinner.setVisibility(android.view.View.GONE);
                if (existing.getImagePath() != null) {
                    holder.imagePath = existing.getImagePath();
                    chooseImgBtn.setText(new File(existing.getImagePath()).getName());
                }
            } else {
                rbText.setChecked(true);
                textInput.setText(existing.getText());
                String style = existing.getTextStyle();
                if (PdfTemplateSettings.STYLE_H1.equals(style)) styleSpinner.setSelection(0);
                else if (PdfTemplateSettings.STYLE_H2.equals(style)) styleSpinner.setSelection(1);
                else styleSpinner.setSelection(2);
            }
        } else {
            rbText.setChecked(true);
            styleSpinner.setSelection(2);
        }

        typeGroup.setOnCheckedChangeListener((g, id) -> {
            boolean isImage = id == rbImage.getId();
            chooseImgBtn.setVisibility(isImage ? android.view.View.VISIBLE : android.view.View.GONE);
            textInput.setVisibility(isImage ? android.view.View.GONE : android.view.View.VISIBLE);
            styleSpinner.setVisibility(isImage ? android.view.View.GONE : android.view.View.VISIBLE);
        });
        topRow.addView(typeGroup);
        topRow.addView(styleSpinner);
        topRow.addView(chooseImgBtn);
        topRow.addView(removeBtn);
        row.addView(topRow);
        row.addView(textInput);
        row.setTag(holder);
        headerBlocksContainer.addView(row);
    }

    private static class BlockRowHolder {
        final RadioButton rbText;
        final RadioButton rbImage;
        final Spinner styleSpinner;
        final EditText textInput;
        final Button chooseImgBtn;
        String imagePath;

        BlockRowHolder(RadioButton rbText, RadioButton rbImage, Spinner styleSpinner, EditText textInput, Button chooseImgBtn) {
            this.rbText = rbText;
            this.rbImage = rbImage;
            this.styleSpinner = styleSpinner;
            this.textInput = textInput;
            this.chooseImgBtn = chooseImgBtn;
        }
    }

    private void openImagePicker(ActivityResultLauncher<Intent> launcher) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        launcher.launch(Intent.createChooser(i, "Select image"));
    }

    private void copyToTemplateDirAndSetLogo(Uri uri) {
        File dir = PdfTemplateStorage.getTemplateDir(this);
        File dest = new File(dir, "logo_" + System.currentTimeMillis() + ".png");
        if (copyUriToFile(uri, dest)) {
            settings.setLogoPath(dest.getAbsolutePath());
            logoPathLabel.setText(dest.getName());
            logoPathLabel.setVisibility(android.view.View.VISIBLE);
            Toast.makeText(this, "Logo set.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToTemplateDirAndSetWatermarkImage(Uri uri) {
        File dir = PdfTemplateStorage.getTemplateDir(this);
        File dest = new File(dir, "watermark_" + System.currentTimeMillis() + ".png");
        if (copyUriToFile(uri, dest)) {
            settings.setWatermarkImagePath(dest.getAbsolutePath());
            watermarkImagePathLabel.setText(dest.getName());
            watermarkImagePathLabel.setVisibility(android.view.View.VISIBLE);
            Toast.makeText(this, "Watermark image set.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToTemplateDirAndSetHeaderBlockImage(Uri uri, int rowIndex) {
        ViewGroup row = (ViewGroup) headerBlocksContainer.getChildAt(rowIndex);
        if (row == null) return;
        Object tag = row.getTag();
        if (!(tag instanceof BlockRowHolder)) return;
        BlockRowHolder h = (BlockRowHolder) tag;
        File dir = PdfTemplateStorage.getTemplateDir(this);
        File dest = new File(dir, "header_img_" + rowIndex + "_" + System.currentTimeMillis() + ".png");
        if (copyUriToFile(uri, dest)) {
            h.imagePath = dest.getAbsolutePath();
            h.chooseImgBtn.setText(dest.getName());
            Toast.makeText(this, "Image set for block.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean copyUriToFile(Uri uri, File dest) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) return false;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Error copying file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Saves current settings as default, saves as named template using the template name field,
     * then clears all fields so the user can create another template.
     */
    private void saveSettingsAndClear() {
        android.widget.EditText nameInput = findViewById(R.id.templateNameInput);
        String name = nameInput != null ? nameInput.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a template name.", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.EditText mainHeaderInput = findViewById(R.id.mainHeaderInput);
        settings.setMainHeaderText(mainHeaderInput != null ? mainHeaderInput.getText().toString().trim() : "");
        Spinner colorSpinner = findViewById(R.id.mainHeaderColorSpinner);
        int colorIndex = colorSpinner != null ? colorSpinner.getSelectedItemPosition() : 0;
        if (colorIndex >= 0 && colorIndex < MAIN_HEADER_COLOR_HEX.length) {
            settings.setMainHeaderColorHex(MAIN_HEADER_COLOR_HEX[colorIndex]);
        }
        Spinner headerSizeSpinner = findViewById(R.id.headerSizeSpinner);
        int headerSizeIndex = headerSizeSpinner != null ? headerSizeSpinner.getSelectedItemPosition() : 0;
        if (headerSizeIndex >= 0 && headerSizeIndex < HEADER_SIZE_VALUES.length) {
            settings.setHeaderSize(HEADER_SIZE_VALUES[headerSizeIndex]);
        }
        Spinner bodyTextSizeSpinner = findViewById(R.id.bodyTextSizeSpinner);
        int bodyTextSizeIndex = bodyTextSizeSpinner != null ? bodyTextSizeSpinner.getSelectedItemPosition() : 0;
        if (bodyTextSizeIndex >= 0 && bodyTextSizeIndex < BODY_TEXT_SIZE_VALUES.length) {
            settings.setBodyTextSize(BODY_TEXT_SIZE_VALUES[bodyTextSizeIndex]);
        }
        String footerText = footerTextInput != null ? footerTextInput.getText().toString().trim() : "";
        settings.setFooterText(footerText.isEmpty() ? null : footerText);
        settings.setWatermarkEnabled(((CheckBox) findViewById(R.id.watermarkEnabled)).isChecked());
        settings.setWatermarkText(watermarkTextInput.getText().toString());
        if (((RadioButton) findViewById(R.id.watermarkTypeImage)).isChecked()) {
            settings.setWatermarkType(PdfTemplateSettings.WATERMARK_IMAGE);
        } else {
            settings.setWatermarkType(PdfTemplateSettings.WATERMARK_TEXT);
        }

        List<PdfTemplateSettings.HeaderBlock> blocks = new ArrayList<>();
        for (int i = 0; i < headerBlocksContainer.getChildCount(); i++) {
            ViewGroup row = (ViewGroup) headerBlocksContainer.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof BlockRowHolder)) continue;
            BlockRowHolder h = (BlockRowHolder) tag;
            PdfTemplateSettings.HeaderBlock b = new PdfTemplateSettings.HeaderBlock();
            if (h.rbImage.isChecked()) {
                b.setBlockType(PdfTemplateSettings.BLOCK_IMAGE);
                b.setImagePath(h.imagePath);
            } else {
                b.setBlockType(PdfTemplateSettings.BLOCK_TEXT);
                b.setText(h.textInput.getText().toString());
                int pos = h.styleSpinner.getSelectedItemPosition();
                b.setTextStyle(pos == 0 ? PdfTemplateSettings.STYLE_H1 : pos == 1 ? PdfTemplateSettings.STYLE_H2 : PdfTemplateSettings.STYLE_BODY);
            }
            blocks.add(b);
        }
        settings.setHeaderBlocks(blocks);

        storage.save(settings);
        SavedTemplate t = buildSavedTemplateFromUi();
        t.setName(name);
        boolean saved = storage.addOrUpdateSavedTemplate(userName, t);
        if (!saved) {
            Toast.makeText(this, getString(R.string.template_limit_reached), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Template \"" + name + "\" saved. Use View templates to use it.", Toast.LENGTH_SHORT).show();

        clearAllFields();
        // Do not save empty settings here: storage keeps the template we just saved so "My Template" in Create Report uses it
    }

    private void clearAllFields() {
        android.widget.EditText nameInput = findViewById(R.id.templateNameInput);
        if (nameInput != null) nameInput.setText("");
        android.widget.EditText mainHeaderInput = findViewById(R.id.mainHeaderInput);
        if (mainHeaderInput != null) mainHeaderInput.setText("");
        settings.setMainHeaderText("");
        settings.setMainHeaderColorHex("#0000FF");
        Spinner colorSpinner = findViewById(R.id.mainHeaderColorSpinner);
        if (colorSpinner != null) colorSpinner.setSelection(0);
        settings.setHeaderSize(PdfTemplateSettings.HEADER_SIZE_DEFAULT);
        Spinner headerSizeSpinner = findViewById(R.id.headerSizeSpinner);
        if (headerSizeSpinner != null) headerSizeSpinner.setSelection(0);
        settings.setBodyTextSize(PdfTemplateSettings.BODY_TEXT_SIZE_DEFAULT);
        Spinner bodyTextSizeSpinner = findViewById(R.id.bodyTextSizeSpinner);
        if (bodyTextSizeSpinner != null) bodyTextSizeSpinner.setSelection(0);
        settings.setFooterText(null);
        if (footerTextInput != null) footerTextInput.setText("");
        settings.setLogoPath(null);
        logoPathLabel.setText("");
        logoPathLabel.setVisibility(android.view.View.GONE);
        ((CheckBox) findViewById(R.id.watermarkEnabled)).setChecked(false);
        watermarkTextInput.setText("");
        ((RadioButton) findViewById(R.id.watermarkTypeText)).setChecked(true);
        chooseWatermarkImageButton.setVisibility(android.view.View.GONE);
        watermarkTextInput.setVisibility(android.view.View.VISIBLE);
        settings.setWatermarkImagePath(null);
        watermarkImagePathLabel.setText("");
        watermarkImagePathLabel.setVisibility(android.view.View.GONE);
        settings.setHeaderBlocks(new ArrayList<>());
        headerBlocksContainer.removeAllViews();
    }

    private SavedTemplate buildSavedTemplateFromUi() {
        SavedTemplate t = new SavedTemplate();
        android.widget.EditText mainHeaderInput = findViewById(R.id.mainHeaderInput);
        t.setMainHeaderText(mainHeaderInput != null ? mainHeaderInput.getText().toString().trim() : "");
        Spinner colorSpinner = findViewById(R.id.mainHeaderColorSpinner);
        int colorIndex = colorSpinner != null ? colorSpinner.getSelectedItemPosition() : 0;
        if (colorIndex >= 0 && colorIndex < MAIN_HEADER_COLOR_HEX.length) {
            t.setMainHeaderColorHex(MAIN_HEADER_COLOR_HEX[colorIndex]);
        } else {
            t.setMainHeaderColorHex(settings.getMainHeaderColorHex());
        }
        t.setHeaderSize(settings.getHeaderSize());
        t.setBodyTextSize(settings.getBodyTextSize());
        t.setFooterText(settings.getFooterText());
        t.setLogoPath(settings.getLogoPath());
        t.setWatermarkEnabled(((CheckBox) findViewById(R.id.watermarkEnabled)).isChecked());
        t.setWatermarkText(watermarkTextInput.getText().toString());
        t.setWatermarkType(((RadioButton) findViewById(R.id.watermarkTypeImage)).isChecked() ? PdfTemplateSettings.WATERMARK_IMAGE : PdfTemplateSettings.WATERMARK_TEXT);
        t.setWatermarkImagePath(settings.getWatermarkImagePath());
        List<PdfTemplateSettings.HeaderBlock> blocks = new ArrayList<>();
        for (int i = 0; i < headerBlocksContainer.getChildCount(); i++) {
            ViewGroup row = (ViewGroup) headerBlocksContainer.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof BlockRowHolder)) continue;
            BlockRowHolder h = (BlockRowHolder) tag;
            PdfTemplateSettings.HeaderBlock b = new PdfTemplateSettings.HeaderBlock();
            if (h.rbImage.isChecked()) {
                b.setBlockType(PdfTemplateSettings.BLOCK_IMAGE);
                b.setImagePath(h.imagePath);
            } else {
                b.setBlockType(PdfTemplateSettings.BLOCK_TEXT);
                b.setText(h.textInput.getText().toString());
                int pos = h.styleSpinner.getSelectedItemPosition();
                b.setTextStyle(pos == 0 ? PdfTemplateSettings.STYLE_H1 : pos == 1 ? PdfTemplateSettings.STYLE_H2 : PdfTemplateSettings.STYLE_BODY);
            }
            blocks.add(b);
        }
        t.setHeaderBlocks(blocks);
        return t;
    }
}
