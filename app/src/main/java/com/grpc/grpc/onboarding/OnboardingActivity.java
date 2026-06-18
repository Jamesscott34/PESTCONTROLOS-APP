package com.grpc.grpc.onboarding;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.grpc.grpc.main.MainActivity;

/**
 * First-login walkthrough shown once per Firebase auth UID.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final class Slide {
        final String icon;
        final String title;
        final String description;

        Slide(String icon, String title, String description) {
            this.icon = icon;
            this.title = title;
            this.description = description;
        }
    }

    private static final Slide[] SLIDES = {
            new Slide(
                    "\uD83D\uDCCB",
                    "Welcome to PestControlOS",
                    "Manage your contracts, jobs, and reports all in one place. Tap through to learn the key screens."
            ),
            new Slide(
                    "\uD83D\uDCC5",
                    "Work View",
                    "Your daily calendar. See every job, contract visit, and follow-up scheduled for the day. Tap any slot to add or edit."
            ),
            new Slide(
                    "\uD83D\uDCC4",
                    "Reports & Maps",
                    "Create service reports, quotations, and site maps directly from a contract. PDFs are saved and uploaded automatically."
            ),
            new Slide(
                    "\uD83D\uDD14",
                    "Notifications & Jobs",
                    "You will be notified when jobs are assigned to you and when contracts are approaching their due date. Tap Notifications on the main screen to see them."
            )
    };

    private ViewPager2 viewPager;
    private Button nextButton;
    private View[] dotViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        markOnboardingShown();
        setContentView(buildRootView());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() == 0) {
                    finish();
                } else {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
                }
            }
        });
    }

    private View buildRootView() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout topBar = new FrameLayout(this);
        Button skipButton = new Button(this);
        skipButton.setText("Skip");
        skipButton.setAllCaps(false);
        skipButton.setOnClickListener(v -> finishOnboarding());
        FrameLayout.LayoutParams skipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        skipLp.gravity = Gravity.END;
        topBar.addView(skipButton, skipLp);
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        viewPager = new ViewPager2(this);
        viewPager.setAdapter(new SlideAdapter());
        LinearLayout.LayoutParams vpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        vpLp.topMargin = dp(16);
        vpLp.bottomMargin = dp(16);
        root.addView(viewPager, vpLp);

        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotViews = new View[SLIDES.length];
        int dotSize = dp(8);
        int dotMargin = dp(4);
        for (int i = 0; i < SLIDES.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.setMargins(dotMargin, 0, dotMargin, 0);
            dot.setLayoutParams(dotLp);
            dot.setBackground(makeDotDrawable());
            dotViews[i] = dot;
            dotsRow.addView(dot);
        }
        root.addView(dotsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout bottomBar = new FrameLayout(this);
        nextButton = new Button(this);
        nextButton.setText("Next");
        nextButton.setAllCaps(false);
        nextButton.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < SLIDES.length - 1) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });
        FrameLayout.LayoutParams nextLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nextLp.gravity = Gravity.END;
        nextLp.topMargin = dp(16);
        bottomBar.addView(nextButton, nextLp);
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        updateDots(0);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                nextButton.setText(position == SLIDES.length - 1 ? "Get Started" : "Next");
            }
        });

        return root;
    }

    private void markOnboardingShown() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                getSharedPreferences("GRPC", MODE_PRIVATE)
                        .edit()
                        .putBoolean("ONBOARDING_SHOWN_" + user.getUid(), true)
                        .apply();
            }
        } catch (Exception ignored) {
        }
    }

    private void finishOnboarding() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("USER_EMAIL", getIntent().getStringExtra("USER_EMAIL"));
        if (getIntent().hasExtra("USER_NAME")) {
            intent.putExtra("USER_NAME", getIntent().getStringExtra("USER_NAME"));
        }
        startActivity(intent);
        finish();
    }

    private void updateDots(int selected) {
        if (dotViews == null) return;
        for (int i = 0; i < dotViews.length; i++) {
            dotViews[i].setAlpha(i == selected ? 1f : 0.35f);
        }
    }

    private GradientDrawable makeDotDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xFF6200EE);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class SlideAdapter extends RecyclerView.Adapter<SlideAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout page = new LinearLayout(parent.getContext());
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER);
            page.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            int innerPad = dp(16);
            page.setPadding(innerPad, innerPad, innerPad, innerPad);

            TextView icon = new TextView(parent.getContext());
            icon.setTextSize(56f);
            icon.setGravity(Gravity.CENTER);
            page.addView(icon);

            TextView title = new TextView(parent.getContext());
            title.setTextSize(22f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = dp(24);
            page.addView(title, titleLp);

            TextView description = new TextView(parent.getContext());
            description.setTextSize(16f);
            description.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = dp(16);
            page.addView(description, descLp);

            return new Holder(page, icon, title, description);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Slide slide = SLIDES[position];
            holder.icon.setText(slide.icon);
            holder.title.setText(slide.title);
            holder.description.setText(slide.description);
        }

        @Override
        public int getItemCount() {
            return SLIDES.length;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView icon;
            final TextView title;
            final TextView description;

            Holder(LinearLayout page, TextView icon, TextView title, TextView description) {
                super(page);
                this.icon = icon;
                this.title = title;
                this.description = description;
            }
        }
    }
}
