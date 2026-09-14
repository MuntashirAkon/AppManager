// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;

final class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.ViewHolder> {
    private final PdfRenderController mRenderController;
    private final int mTargetWidth;
    private int mPageCount;
    private boolean mCleared;

    PdfPageAdapter(@NonNull Context context, @NonNull PdfRenderController renderController) {
        mRenderController = renderController;
        mTargetWidth = Math.min(context.getResources().getDisplayMetrics().widthPixels, 2048);
        setHasStableIds(true);
    }

    void setPageCount(int pageCount) {
        mPageCount = pageCount;
        notifyDataSetChanged();
    }

    void clear() {
        mCleared = true;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return mPageCount;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pdf_page, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        releaseHolderBitmap(holder);
        holder.bind(position);
        holder.mRenderTask = mRenderController.renderPage(position, mTargetWidth, new PdfRenderController.PageListener() {
            @Override
            public void onPageRendered(@NonNull Bitmap bitmap) {
                if (mCleared || !Integer.valueOf(position).equals(holder.mPageTag)) {
                    mRenderController.releaseBitmap(bitmap);
                    return;
                }
                holder.mBitmap = bitmap;
                holder.mImageView.setImageBitmap(bitmap);
                holder.mPageDivider.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                if (!mCleared && Integer.valueOf(position).equals(holder.mPageTag)) {
                    holder.mImageView.setImageResource(io.github.muntashirakon.ui.R.drawable.ic_caution);
                    holder.mPageDivider.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.cancelRender();
        releaseHolderBitmap(holder);
        holder.mPageTag = null;
        holder.mImageView.setImageDrawable(null);
        holder.mPageDivider.setVisibility(View.GONE);
        super.onViewRecycled(holder);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView mImageView;
        final View mPageDivider;
        @Nullable
        Integer mPageTag;
        @Nullable
        Bitmap mBitmap;
        @Nullable
        Future<?> mRenderTask;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mImageView = itemView.findViewById(R.id.page_image);
            mPageDivider = itemView.findViewById(R.id.page_divider);
        }

        void bind(int page) {
            cancelRender();
            mPageTag = page;
            mImageView.setImageDrawable(null);
            mPageDivider.setVisibility(View.GONE);
        }

        void cancelRender() {
            if (mRenderTask != null) {
                mRenderTask.cancel(true);
                mRenderTask = null;
            }
        }

        @Nullable
        Bitmap detachBitmap() {
            Bitmap bitmap = mBitmap;
            mBitmap = null;
            return bitmap;
        }
    }

    private void releaseHolderBitmap(@NonNull ViewHolder holder) {
        Bitmap bitmap = holder.detachBitmap();
        if (bitmap != null) {
            mRenderController.releaseBitmap(bitmap);
        }
    }
}
