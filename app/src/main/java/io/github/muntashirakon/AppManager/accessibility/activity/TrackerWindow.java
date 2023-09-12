// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Display;
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
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.widget.TextInputTextView;

public class TrackerWindow implements View.OnTouchListener {
    public final WindowManager mWindowManager;
    private final WindowManager.LayoutParams mWindowLayoutParams;
    private final View mView;
    private final ShapeableImageView mIconView;
    private final MaterialCardView mContentView;
    private final TextInputTextView mPackageNameView;
    private final TextInputTextView mClassNameView;
    private final TextInputTextView mClassHierarchyView;
    private final MaterialButton mPlayPauseButton;
    private final Point mWindowSize = new Point(0, 0);
    private final Point mWindowPosition = new Point(0, 0);
    private final Point mPressPosition = new Point(0, 0);
    private final int mMaxWidth;

    public boolean mPaused = false;
    public boolean mIconified = false;
    public boolean mViewAttached = false;
    public boolean mDragging = false;

    @SuppressLint("ClickableViewAccessibility")
    public TrackerWindow(@NonNull Context context) {
        Context themedContext = AppearanceUtils.getThemedContext(context, true);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        Display display = mWindowManager.getDefaultDisplay();
        int displayWidth = display.getWidth();
        display.getRealSize(mWindowSize);
        mMaxWidth = (displayWidth / 2) + 300; // FIXME: 5/2/23 Find a better way to represent a display
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mWindowLayoutParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type, flags, PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.gravity = Gravity.CENTER;
        mWindowLayoutParams.width = mMaxWidth;
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
            if (TextUtils.isEmpty(packageName)) {
                return false;
            }
            copyText("Package name", packageName);
            return true;
        });
        mClassNameView.setOnLongClickListener(v -> {
            Editable className = mClassNameView.getText();
            if (TextUtils.isEmpty(className)) {
                return false;
            }
            copyText("Class name", className);
            return true;
        });
        mClassHierarchyView.setOnLongClickListener(v -> {
            Editable hierarchy = mClassHierarchyView.getText();
            if (TextUtils.isEmpty(hierarchy)) {
                return false;
            }
            copyText("Class hierarchy", hierarchy);
            return true;
        });
        mView.findViewById(R.id.info).setOnClickListener(v -> {
            Editable packageName = mPackageNameView.getText();
            if (TextUtils.isEmpty(packageName)) {
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
        mView.findViewById(R.id.mini).setOnClickListener(v -> iconify());
        mPlayPauseButton.setOnClickListener(v -> {
            mPaused = !mPaused;
            mPlayPauseButton.setIconResource(mPaused ? R.drawable.ic_play_arrow : R.drawable.ic_pause);
        });
        mView.findViewById(android.R.id.closeButton).setOnClickListener(v -> dismiss());
        mIconView.setVisibility(View.GONE);
        mIconView.setOnClickListener(v -> expand());
        mView.findViewById(R.id.drag).setOnTouchListener(this);
        mIconView.setOnTouchListener(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Point point;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            mDragging = false;
            point = new Point((int) event.getRawX(), (int) event.getRawY());
            mPressPosition.set(point.x, point.y);
            mWindowPosition.set(mWindowLayoutParams.x, mWindowLayoutParams.y);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            mDragging = true;
            point = new Point((int) event.getRawX(), (int) event.getRawY());
            int delX = point.x - mPressPosition.x;
            int delY = point.y - mPressPosition.y;
            mWindowLayoutParams.x = mWindowPosition.x + delX;
            mWindowLayoutParams.y = mWindowPosition.y + delY;
            updateLayout();
            return true;
        }
        if (!mDragging && v == mIconView && action == MotionEvent.ACTION_UP) {
            point = new Point((int) event.getRawX(), (int) event.getRawY());
            int delX = point.x - mPressPosition.x;
            int delY = point.y - mPressPosition.y;
            if (delX < 1 && delY < 1) {
                v.performClick();
                return true;
            }
        }
        return false;
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

    private void iconify() {
        mPaused = true;
        mIconified = true;
        // Window position may need to be adjusted to display the icon
        // (0,0) is middle
        int height = -mWindowSize.y / 2;
        if (mWindowLayoutParams.y < height) {
            mWindowPosition.y = height;
            mWindowLayoutParams.y = height;
        }
        mIconView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        updateLayout();
    }

    private void expand() {
        mContentView.setVisibility(View.VISIBLE);
        mIconView.setVisibility(View.GONE);
        mPaused = false;
        mIconified = false;
        // Window position may need to be adjusted to display the drag handle
        // (0,0) is middle
        int width = (-mWindowSize.x + mMaxWidth) / 2;
        if (mWindowLayoutParams.x < width) {
            mWindowPosition.x = width;
            mWindowLayoutParams.x = width;
        }
        updateLayout();
    }

    private void updateLayout() {
        mWindowLayoutParams.width = mIconified ? WindowManager.LayoutParams.WRAP_CONTENT : mMaxWidth;
        mWindowManager.updateViewLayout(mView, mWindowLayoutParams);
    }

    private void copyText(CharSequence label, CharSequence content) {
        Utils.copyToClipboard(mView.getContext(), label, content);
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