package com.example.xyzreader.ui;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        final View toolbarContainerView = findViewById(R.id.toolbar_container);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        adapter = new Adapter(null);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        GridLayoutManager gridLayoutManager =
                new GridLayoutManager(this, columnCount);
        mRecyclerView.setLayoutManager(gridLayoutManager);

        LoaderManager.getInstance(this).initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(@NonNull androidx.loader.content.Loader<Cursor> loader, Cursor cursor) {
        adapter.updateList(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull androidx.loader.content.Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition()))));
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                        + "<br/>" + " by "
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            }
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));
        }

        void updateList(Cursor newCursor) {

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new CursorCallback<Cursor>(this.mCursor, newCursor) {
                @Override
                public boolean areRowContentsTheSame(Cursor oldCursor, Cursor newCursor) {
                    boolean contentsTheSame = (oldCursor.getString(ArticleLoader.Query.TITLE).equals(newCursor.getString(ArticleLoader.Query.TITLE)));
                    Log.i("Adapter", "contents the same: " + contentsTheSame);
                    return contentsTheSame;
                }

                // as there is no unique id in the data, we simply regard the
                // article title as the unique id, as article titles are usually
                // different
                @Override
                public boolean areCursorRowsTheSame(Cursor oldCursor, Cursor newCursor) {
                    boolean cursorRowsTheSame = (oldCursor.getString(ArticleLoader.Query.TITLE).equals(newCursor.getString(ArticleLoader.Query.TITLE)));
                    Log.i("Adapter", "cursor rows the same: " + cursorRowsTheSame);
                    return cursorRowsTheSame;
                }
            });
            diffResult.dispatchUpdatesTo(this);
            this.mCursor = newCursor;
        }

        @Override
        public int getItemCount() {
            if (mCursor == null) return 0;
            return mCursor.getCount();
        }
    }

    public abstract class CursorCallback<C extends Cursor> extends DiffUtil.Callback {
        private final C newCursor;
        private final C oldCursor;

        public CursorCallback(C newCursor, C oldCursor) {
            this.newCursor = newCursor;
            this.oldCursor = oldCursor;
        }

        @Override
        public int getOldListSize() {
            return oldCursor == null ? 0 : oldCursor.getCount();
        }

        @Override
        public int getNewListSize() {
            return newCursor == null? 0 : newCursor.getCount();
        }

        @Override
        public final boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldCursor.getColumnCount() == newCursor.getColumnCount() && moveCursorsToPosition(oldItemPosition, newItemPosition) && areCursorRowsTheSame(oldCursor, newCursor);
        }

        @Override
        public final boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldCursor.getColumnCount() == newCursor.getColumnCount() && moveCursorsToPosition(oldItemPosition, newItemPosition) && areRowContentsTheSame(oldCursor, newCursor);
        }

        @Nullable
        @Override
        public final Object getChangePayload(int oldItemPosition, int newItemPosition) {
            moveCursorsToPosition(oldItemPosition, newItemPosition);
            return getChangePayload(newCursor, oldCursor);
        }
        @Nullable
        public Object getChangePayload(C newCursor, C oldCursor) {
            return null;
        }

        private boolean moveCursorsToPosition(int oldItemPosition, int newItemPosition) {
            boolean newMoved = newCursor.moveToPosition(newItemPosition);
            boolean oldMoved = oldCursor.moveToPosition(oldItemPosition);
            return newMoved && oldMoved;
        }
        /** Cursors are already moved to positions where you should obtain data by row.
         *  Checks if contents at row are same
         *
         * @param oldCursor Old cursor object
         * @param newCursor New cursor object
         * @return See DiffUtil
         */
        public abstract boolean areRowContentsTheSame(Cursor oldCursor, Cursor newCursor);

        /** Cursors are already moved to positions where you should obtain data from row
         *  Checks if rows are the same, ideally, check by unique id
         * @param oldCursor Old cursor object
         * @param newCursor New cursor object
         * @return See DiffUtil
         */
        public abstract boolean areCursorRowsTheSame(Cursor oldCursor, Cursor newCursor);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }
}
