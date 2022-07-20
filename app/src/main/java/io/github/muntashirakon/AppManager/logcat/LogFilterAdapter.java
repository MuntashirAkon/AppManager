// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;

// Copyright 2012 Nolan Lawson
public class LogFilterAdapter extends ArrayAdapter<LogFilter> {
    LayoutInflater layoutInflater;

    public interface OnClickListener {
        void onClick(ViewGroup parent, View view, int position, LogFilter logFilter);
    }

    private OnClickListener listener;

    public void setOnItemClickListener(OnClickListener listener) {
        this.listener = listener;
    }

    public LogFilterAdapter(FragmentActivity activity, List<LogFilter> items) {
        super(activity, R.layout.item_title_action, items);
        layoutInflater = activity.getLayoutInflater();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.item_title_action, parent, false);
            holder = new ViewHolder();
            holder.textView = convertView.findViewById(R.id.item_title);
            holder.actionButton = convertView.findViewById(R.id.item_action);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        final LogFilter logFilter = getItem(position);
        holder.textView.setText(logFilter.name);
        View finalConvertView = convertView;
        convertView.setBackgroundResource(R.drawable.item_transparent);
        convertView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(parent, finalConvertView, position, logFilter);
            }
        });
        holder.actionButton.setOnClickListener(v -> {
            new Thread(() -> AppsDb.getInstance().logFilterDao().delete(logFilter)).start();
            remove(logFilter);
            notifyDataSetChanged();
        });
        return convertView;
    }

    private static class ViewHolder {
        TextView textView;
        MaterialButton actionButton;
    }
}
