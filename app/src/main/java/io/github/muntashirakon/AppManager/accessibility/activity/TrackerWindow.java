// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.widget.TextInputTextView;

public class TrackerWindow {
    public final WindowManager mWindowManager;
    private final WindowManager.LayoutParams mWindowLayoutParams;
    private final View mView;
    private final ShapeableImageView mIconView;
    private final MaterialCardView mContentView;
    private final TextInputTextView mPackageNameView;
    private final TextInputTextView mClassNameView;
    private final TextInputTextView mClassHierarchyView;
    private final MaterialButton mPlayPauseButton;
    private final Point mWindowPosition = new Point(0, 0);
    private final Point mPressPosition = new Point(0, 0);

    public boolean mPaused = false;
    public boolean mViewAttached = false;

    @SuppressLint("ClickableViewAccessibility")
    public TrackerWindow(@NonNull Context context) {
        Context themedContext = AppearanceUtils.getThemedContext(context);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int displayWidth = mWindowManager.getDefaultDisplay().getWidth();
        mWindowLayoutParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.gravity = Gravity.CENTER;
        mWindowLayoutParams.width = (displayWidth / 2) + 300; // FIXME: 5/2/23 Find a better way to represent a display
        mWindowLayoutParams.windowAnimations = android.R.style.Animation_Toast;

        mView = View.inflate(themedContext, R.layout.window_activity_tracker, null);
        mIconView = mView.findViewById(R.id.icon);
        mContentView = mView.findViewById(R.id.content);
        mPackageNameView = mView.findViewById(R.id.package_name);
        mClassNameView = mView.findViewById(R.id.class_name);
        mClassHierarchyView = mView.findViewById(R.id.class_hierarchy);
        mPlayPauseButton = mView.findViewById(R.id.action_play_pause);
        mPackageNameView.setOnLongClickListener(v -> {
            Editable packageName = mPackageNameView.getText();
            if (TextUtilsCompat.isEmpty(packageName)) {
                return false;
            }
            copyText("Package name", packageName);
            return true;
        });
        mClassNameView.setOnLongClickListener(v -> {
            Editable className = mClassNameView.getText();
            if (TextUtilsCompat.isEmpty(className)) {
                return false;
            }
            copyText("Class name", className);
            return true;
        });
        mClassHierarchyView.setOnLongClickListener(v -> {
            Editable hierarchy = mClassHierarchyView.getText();
            if (TextUtilsCompat.isEmpty(hierarchy)) {
                return false;
            }
            copyText("Class hierarchy", hierarchy);
            return true;
        });
        mView.findViewById(R.id.info).setOnClickListener(v -> {
            Editable packageName = mPackageNameView.getText();
            if (TextUtilsCompat.isEmpty(packageName)) {
                return;
            }
            Intent appInfoIntent = AppDetailsActivity.getIntent(context, packageName.toString(), UserHandleHidden.myUserId(), true);
            appInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(appInfoIntent);
            } catch (Throwable th) {
                UIUtils.displayLongToast(th.getMessage());
            }
        });
        mView.findViewById(R.id.mini).setOnClickListener(v -> {
            mPaused = true;
            mIconView.setVisibility(View.VISIBLE);
            mContentView.setVisibility(View.GONE);
        });
        mPlayPauseButton.setOnClickListener(v -> {
            mPaused = !mPaused;
            mPlayPauseButton.setIconResource(mPaused ? R.drawable.ic_play_arrow : R.drawable.ic_pause);
        });
        mView.findViewById(android.R.id.closeButton).setOnClickListener(v -> dismiss());
        mIconView.setVisibility(View.GONE);
        mIconView.setOnClickListener(v -> {
            mContentView.setVisibility(View.VISIBLE);
            mIconView.setVisibility(View.GONE);
            mPaused = false;
        });

        mView.setOnTouchListener((view, event) -> {
            Point point = new Point((int) event.getRawX(), (int) event.getRawY());
            int action = event.getAction();

            if (action == MotionEvent.ACTION_DOWN) {
                mPressPosition.set(point.x, point.y);
                mWindowPosition.set(mWindowLayoutParams.x, mWindowLayoutParams.y);
            } else if (action == MotionEvent.ACTION_MOVE) {
                int delX = point.x - mPressPosition.x;
                int delY = point.y - mPressPosition.y;
                mWindowLayoutParams.x = mWindowPosition.x + delX;
                mWindowLayoutParams.y = mWindowPosition.y + delY;
                mWindowManager.updateViewLayout(view, mWindowLayoutParams);
            }
            return true;
        });
    }

    public void showOrUpdate(AccessibilityEvent event) {
        if (!mViewAttached) {
            mViewAttached = true;
            mWindowManager.addView(mView, mWindowLayoutParams);
        }
        if (!mPaused) {
            mPackageNameView.setText(event.getPackageName());
            mClassNameView.setText(event.getClassName());
            mClassHierarchyView.setText(TextUtils.join("\n", getClassHierarchy(event)));
        }
    }

    public void dismiss() {
        AccessibilityMultiplexer.getInstance().enableLeadingActivityTracker(false);
        mViewAttached = false;
        try {
            mWindowManager.removeView(mView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyText(CharSequence label, CharSequence content) {
        ClipboardManager clipboard = (ClipboardManager) mView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content));
        UIUtils.displayShortToast(R.string.copied_to_clipboard);
    }

    @NonNull
    private static List<CharSequence> getClassHierarchy(@NonNull AccessibilityEvent event) {
        List<CharSequence> classHierarchies = new ArrayList<>();
        AccessibilityNodeInfo nodeInfo = event.getSource();
        if (nodeInfo != null) {
            classHierarchies.add(nodeInfo.getClassName());
            int depth = 0;
            while (depth < 20) { // Limit depth to avoid running forever
                AccessibilityNodeInfo tmpNodeInfo = nodeInfo.getParent();
                if (tmpNodeInfo != null) {
                    nodeInfo.recycle();
                    nodeInfo = tmpNodeInfo;
                    classHierarchies.add(nodeInfo.getClassName());
                } else {
                    // Max depth reached
                    break;
                }
                ++depth;
            }
            try {
                if (depth == 20) {
                    classHierarchies.add("...");
                }
            } finally {
                nodeInfo.recycle();
            }
        }
        Collections.reverse(classHierarchies);
        int size = classHierarchies.size();
        if (size <= 1) {
            return classHierarchies;
        }
        classHierarchies.set(0, "┬ " + classHierarchies.get(0));
        for (int i = 1; i < size; ++i) {
            StringBuilder sb = new StringBuilder();
            for (int j = 1; j < i; ++j) {
                sb.append(' ');
            }
            if (i != (size - 1)) {
                sb.append("└┬ ");
            } else sb.append("└─ ");
            sb.append(classHierarchies.get(i));
            classHierarchies.set(i, sb.toString());
        }
        return classHierarchies;
    }
}