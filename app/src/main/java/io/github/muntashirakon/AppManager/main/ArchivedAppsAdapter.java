// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp;

public class ArchivedAppsAdapter extends RecyclerView.Adapter<ArchivedAppsAdapter.ViewHolder> {

    private List<ArchivedApp> archivedApps;
    private final OnRestoreClickListener listener;

    public interface OnRestoreClickListener {
        void onRestoreClicked(ArchivedApp archivedApp);
    }

    public ArchivedAppsAdapter(List<ArchivedApp> archivedApps, OnRestoreClickListener listener) {
        this.archivedApps = archivedApps;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_archived_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArchivedApp archivedApp = archivedApps.get(position);
        holder.appName.setText(archivedApp.appName);
        holder.packageName.setText(archivedApp.packageName);
        holder.restoreButton.setOnClickListener(v -> listener.onRestoreClicked(archivedApp));
    }

    @Override
    public int getItemCount() {
        return archivedApps.size();
    }

    public void updateData(List<ArchivedApp> newArchivedApps) {
        this.archivedApps = newArchivedApps;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appName;
        TextView packageName;
        Button restoreButton;

        ViewHolder(View view) {
            super(view);
            appName = view.findViewById(R.id.app_name);
            packageName = view.findViewById(R.id.package_name);
            restoreButton = view.findViewById(R.id.restore_button);
        }
    }
}
