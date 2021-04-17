/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;

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
        holder.textView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(parent, finalConvertView, position, logFilter);
            }
        });
        holder.actionButton.setOnClickListener(v -> {
            new Thread(() -> AppManager.getDb().logFilterDao().delete(logFilter)).start();
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
