// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evrencoskun.tableview.adapter.AbstractTableAdapter;
import com.evrencoskun.tableview.adapter.recyclerview.holder.AbstractViewHolder;

import io.github.muntashirakon.AppManager.R;

public class SqliteDbTableViewAdapter extends AbstractTableAdapter<ColumnHeader, RecordItemHeader, TableRecordItem> {
    private final Context context;

    public SqliteDbTableViewAdapter(Context context) {
        this.context = context;
    }

    @Override
    @NonNull
    public AbstractViewHolder onCreateCellViewHolder(@NonNull ViewGroup parent, int viewType) {
        View layout = LayoutInflater.from(context).inflate(R.layout.table_view_cell, parent, false);
        return new CellViewHolder(layout);
    }

    @Override
    public void onBindCellViewHolder(@NonNull AbstractViewHolder holder, @Nullable TableRecordItem cellItemModel,
                                     int columnPosition, int rowPosition) {
        CellViewHolder viewHolder = (CellViewHolder) holder;
        viewHolder.textView.setText((String) cellItemModel.data);
        // Resize
        viewHolder.ItemView.getLayoutParams().width = LinearLayout.LayoutParams.WRAP_CONTENT;
        viewHolder.textView.requestLayout();
    }

    @Override
    @NonNull
    public AbstractViewHolder onCreateColumnHeaderViewHolder(@NonNull ViewGroup parent, int viewType) {
        View layout = LayoutInflater.from(context).inflate(R.layout.table_view_column_header, parent, false);
        return new ColumnHeaderViewHolder(layout);
    }

    @Override
    public void onBindColumnHeaderViewHolder(@NonNull AbstractViewHolder holder,
                                             @Nullable ColumnHeader columnHeaderItemModel, int columnPosition) {
        ColumnHeaderViewHolder columnHeaderViewHolder = (ColumnHeaderViewHolder) holder;
        columnHeaderViewHolder.textView.setText((String) columnHeaderItemModel.data);
        // Resize
        columnHeaderViewHolder.container.getLayoutParams().width = LinearLayout.LayoutParams.WRAP_CONTENT;
        columnHeaderViewHolder.textView.requestLayout();
    }

    @Override
    @NonNull
    public AbstractViewHolder onCreateRowHeaderViewHolder(@NonNull ViewGroup parent, int viewType) {
        View layout = LayoutInflater.from(context).inflate(R.layout.table_view_row_header, parent, false);
        return new RowHeaderViewHolder(layout);
    }

    @Override
    public void onBindRowHeaderViewHolder(@NonNull AbstractViewHolder holder, @Nullable RecordItemHeader rowHeaderItemModel,
                                          int rowPosition) {
        RowHeaderViewHolder rowHeaderViewHolder = (RowHeaderViewHolder) holder;
        rowHeaderViewHolder.textView.setText((String) rowHeaderItemModel.data);
    }

    @NonNull
    @Override
    public View onCreateCornerView(@NonNull ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.table_view_corner, parent, false);
    }

    @Override
    public int getColumnHeaderItemViewType(int columnPosition) {
        // The unique ID for this type of column header item
        // If you have different items for Cell View by X (Column) position,
        // then you should fill this method to be able to create different
        // type of CellViewHolder on "onCreateCellViewHolder"
        return 0;
    }

    @Override
    public int getRowHeaderItemViewType(int rowPosition) {
        // The unique ID for this type of row header item
        // If you have different items for Row Header View by Y (Row) position,
        // then you should fill this method to be able create different
        // type of RowHeaderViewHolder on "onCreateRowHeaderViewHolder"
        return 0;
    }

    @Override
    public int getCellItemViewType(int columnPosition) {
        // The unique ID for this type of cell item
        // If you have different items for Cell View by X (Column) position,
        // then you should fill this method to be able create different
        // type of CellViewHolder on "onCreateCellViewHolder"
        return 0;
    }

    public static final class CellViewHolder extends AbstractViewHolder {
        public TextView textView;
        public LinearLayout ItemView;

        public CellViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.cell_data);
            ItemView = itemView.findViewById(R.id.cell_container);
        }
    }

    static class ColumnHeaderViewHolder extends AbstractViewHolder {

        public final TextView textView;
        public final LinearLayout container;

        public ColumnHeaderViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.column_header_textView);
            container = itemView.findViewById(R.id.column_header_container);
        }
    }

    static class RowHeaderViewHolder extends AbstractViewHolder {

        public final TextView textView;

        public RowHeaderViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.row_header_textview);
        }
    }
}

