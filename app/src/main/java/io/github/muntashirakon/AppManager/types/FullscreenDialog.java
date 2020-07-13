package io.github.muntashirakon.AppManager.types;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Objects;

import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;

@SuppressWarnings("unused")
public class FullscreenDialog extends DialogFragment {

    public static final String TAG = "FullscreenDialog";

    private View dialogView;
    private Toolbar toolbar;
    private View contentView;
    private FrameLayout customView;
    private TextView messageView;
    private FragmentActivity context;
    private @StyleRes int themeResId;

    public FullscreenDialog() {}

    public FullscreenDialog(FragmentActivity context) {
        this.context = context;
        this.themeResId = R.style.AppTheme_FullScreenDialog;
        init();
    }

    public FullscreenDialog(FragmentActivity context, @StyleRes int themeResId) {
        this.context = context;
        this.themeResId = themeResId;
        init();
    }

    public void show() {
        show(context.getSupportFragmentManager(), TAG);
    }

    public FullscreenDialog setTitle(CharSequence title) {
        toolbar.setTitle(title);
        return this;
    }

    public FullscreenDialog setTitle(@StringRes int title) {
        toolbar.setTitle(title);
        return this;
    }

    public FullscreenDialog setSubtitle(CharSequence subtitle) {
        toolbar.setSubtitle(subtitle);
        return this;
    }

    public FullscreenDialog setSubtitle(@StringRes int subtitle) {
        toolbar.setSubtitle(subtitle);
        return this;
    }

    @SuppressLint("RestrictedApi")
    public FullscreenDialog setMenu(@MenuRes int menu) {
        toolbar.inflateMenu(menu);
        Menu menu1 = toolbar.getMenu();
        if (menu1 instanceof MenuBuilder) {
            ((MenuBuilder) menu1).setOptionalIconsVisible(true);
        }
        return this;
    }

    @SuppressLint("RestrictedApi")
    public FullscreenDialog setMenu(@NonNull MenuBuilder menu) {
        menu.setOptionalIconsVisible(true);
        toolbar.setMenu(menu, null);
        return this;
    }

    public FullscreenDialog setView(View view) {
        customView.setVisibility(View.VISIBLE);
        contentView.setVisibility(View.GONE);
        customView.removeAllViews();
        customView.addView(view);
        return this;
    }

    public FullscreenDialog setView(@LayoutRes int viewResId) {
        customView.setVisibility(View.VISIBLE);
        contentView.setVisibility(View.GONE);
        View view = context.getLayoutInflater().inflate(viewResId, null);
        customView.removeAllViews();
        customView.addView(view);
        return this;
    }

    public FullscreenDialog setMessage(CharSequence message) {
        customView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        messageView.setText(message);
        return this;
    }

    public FullscreenDialog setMessage(@StringRes int message) {
        customView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        messageView.setText(message);
        return this;
    }

    @SuppressLint("InflateParams")
    protected void init() {
        dialogView = context.getLayoutInflater().inflate(R.layout.dialog_fullscreen, null);
        toolbar = dialogView.findViewById(R.id.toolbar);
        contentView = dialogView.findViewById(R.id.content);
        customView = dialogView.findViewById(R.id.custom);
        messageView = dialogView.findViewById(R.id.message);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, themeResId);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (dialogView == null) return null;
        return dialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (dialogView == null) return;
        toolbar.setNavigationOnClickListener(v -> dismiss());
//        toolbar.inflateMenu(R.menu.activity_main_actions);
        toolbar.setOnMenuItemClickListener(item -> {
            dismiss();
            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (dialogView == null) {
            dismiss();
            return;
        }
        Dialog dialog = requireDialog();
        Objects.requireNonNull(dialog.getWindow()).setFlags(
                WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW,
                WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW);
        dialog.getWindow().setWindowAnimations(R.style.AppTheme_FullScreenDialog_Animation);
    }
}
