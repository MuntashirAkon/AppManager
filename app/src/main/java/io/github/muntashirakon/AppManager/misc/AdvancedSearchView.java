// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.TintTypedArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;

public class AdvancedSearchView extends SearchView {
    @IntDef({SEARCH_TYPE_CONTAINS, SEARCH_TYPE_PREFIX, SEARCH_TYPE_SUFFIX, SEARCH_TYPE_REGEX})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchType {
    }

    /**
     * Search using {@link String#contains(CharSequence)}.
     */
    public static final int SEARCH_TYPE_CONTAINS = 1;
    /**
     * Search using {@link String#startsWith(String)}.
     */
    public static final int SEARCH_TYPE_PREFIX = 2;
    /**
     * Search using {@link String#endsWith(String)}.
     */
    public static final int SEARCH_TYPE_SUFFIX = 3;
    /**
     * Search using {@link String#matches(String)} or {@link java.util.regex.Pattern}.
     */
    public static final int SEARCH_TYPE_REGEX = 4;

    @SearchType
    private int mType = SEARCH_TYPE_CONTAINS;
    private OnQueryTextListener mOnQueryTextListener;
    private final CharSequence mQueryHint;
    private final ImageView mSearchButton;
    private final SearchView.OnQueryTextListener mOnQueryTextListenerSuper = new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            if (mOnQueryTextListener != null) {
                return mOnQueryTextListener.onQueryTextSubmit(query, mType);
            }
            return false;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if (mOnQueryTextListener != null) {
                return mOnQueryTextListener.onQueryTextChange(newText, mType);
            }
            return false;
        }
    };
    private final View.OnClickListener onClickSearchIcon = v -> {
        PopupMenu popupMenu = new PopupMenu(getContext(), v);
        popupMenu.inflate(R.menu.view_advanced_search_type_selections);
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_search_type_contains) {
                mType = SEARCH_TYPE_CONTAINS;
            } else if (id == R.id.action_search_type_prefix) {
                mType = SEARCH_TYPE_PREFIX;
            } else if (id == R.id.action_search_type_suffix) {
                mType = SEARCH_TYPE_SUFFIX;
            } else if (id == R.id.action_search_type_regex) {
                mType = SEARCH_TYPE_REGEX;
            }
            if (mOnQueryTextListener != null) {
                mOnQueryTextListener.onQueryTextChange(getQuery().toString(), mType);
            }
            updateQueryHint();
            return true;
        });
        popupMenu.show();
    };

    public AdvancedSearchView(@NonNull Context context) {
        this(context, null);
    }

    public AdvancedSearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.searchViewStyle);
    }

    @SuppressLint("RestrictedApi")
    public AdvancedSearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSearchButton = findViewById(R.id.search_mag_icon);
        mSearchButton.setImageResource(R.drawable.ic_filter_menu_outline);
        mSearchButton.setOnClickListener(onClickSearchIcon);
        final TintTypedArray a = TintTypedArray.obtainStyledAttributes(context,
                attrs, R.styleable.SearchView, defStyleAttr, 0);
        mQueryHint = a.getText(R.styleable.SearchView_queryHint);
        a.recycle();
        updateQueryHint();
    }

    public void setOnQueryTextListener(@Nullable OnQueryTextListener listener) {
        if (listener == null) return;
        mOnQueryTextListener = listener;
        super.setOnQueryTextListener(mOnQueryTextListenerSuper);
    }

    /**
     * @deprecated This method is ignored. Use {@link #setOnQueryTextListener(OnQueryTextListener)} instead.
     */
    @Override
    public final void setOnQueryTextListener(SearchView.OnQueryTextListener listener) {
        throw new UnsupportedOperationException("Wrong function. Use the other function by the same name.");
    }

    public static boolean matches(String query, String text, @SearchType int type) {
        switch (type) {
            case SEARCH_TYPE_CONTAINS:
                return text.contains(query);
            case SEARCH_TYPE_PREFIX:
                return text.startsWith(query);
            case SEARCH_TYPE_SUFFIX:
                return text.endsWith(query);
            case SEARCH_TYPE_REGEX:
                return text.matches(query);
        }
        return false;
    }

    private void updateQueryHint() {
        setQueryHint(mQueryHint + " (" + getQueryHint(mType) + ")");
    }

    @NonNull
    private CharSequence getQueryHint(@SearchType int type) {
        switch (type) {
            default:
            case SEARCH_TYPE_CONTAINS:
                return getContext().getString(R.string.search_type_contains);
            case SEARCH_TYPE_PREFIX:
                return getContext().getString(R.string.search_type_prefix);
            case SEARCH_TYPE_REGEX:
                return getContext().getString(R.string.search_type_regular_expressions);
            case SEARCH_TYPE_SUFFIX:
                return getContext().getString(R.string.search_type_suffix);
        }
    }

    public interface OnQueryTextListener {
        boolean onQueryTextChange(String newText, @SearchType int type);

        boolean onQueryTextSubmit(String query, @SearchType int type);
    }
}
