package tk.wasdennnoch.androidn_ify.systemui.qs;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import de.robv.android.xposed.XposedHelpers;
import tk.wasdennnoch.androidn_ify.R;
import tk.wasdennnoch.androidn_ify.XposedHook;
import tk.wasdennnoch.androidn_ify.systemui.notifications.StatusBarHeaderHooks;
import tk.wasdennnoch.androidn_ify.ui.AddTileActivity;
import tk.wasdennnoch.androidn_ify.ui.SettingsActivity;
import tk.wasdennnoch.androidn_ify.utils.ConfigUtils;
import tk.wasdennnoch.androidn_ify.utils.ResourceUtils;

public class DetailViewManager {

    private static final String TAG = "DetailViewManager";
    private static final String CLASS_DETAIL_ADAPTER = "com.android.systemui.qs.QSTile$DetailAdapter";

    private static DetailViewManager sInstance;

    private Context mContext;
    private ViewGroup mStatusBarHeaderView;
    private ViewGroup mQsPanel;
    private TextView mEditButton;
    private boolean mHasEditPanel;
    private boolean mTileAdapterIsInvalid;

    private EditRecyclerView mRecyclerView;
    private Object mEditAdapter;
    private TileAdapter mTileAdapter;

    public static DetailViewManager getInstance() {
        if (sInstance == null)
            throw new IllegalStateException("Must initialize DetailViewManager first");
        return sInstance;
    }

    public static void init(Context context, ViewGroup statusBarHeaderView, ViewGroup qsPanel, TextView editButton, boolean hasEditPanel) {
        sInstance = new DetailViewManager(context, statusBarHeaderView, qsPanel, editButton, hasEditPanel);
    }

    private DetailViewManager(Context context, ViewGroup statusBarHeaderView, ViewGroup qsPanel, TextView editButton, boolean hasEditPanel) {
        mContext = context;
        mStatusBarHeaderView = statusBarHeaderView;
        mQsPanel = qsPanel;
        mEditButton = editButton;
        mHasEditPanel = hasEditPanel;
    }

    public void saveChanges() {
        mTileAdapter.saveChanges();
    }

    public void invalidTileAdapter() {
        mTileAdapterIsInvalid = true;
    }

    public void showEditView(ArrayList<Object> records, int x, int y) {
        if (records == null) {
            Toast.makeText(mContext, "Couldn't open edit view; mRecords == null", Toast.LENGTH_SHORT).show();
            XposedHook.logE(TAG, "Couldn't open edit view; mRecords == null", null);
            return;
        }
        if (mEditAdapter == null)
            createEditAdapter(records);
        if (mTileAdapterIsInvalid) {
            mTileAdapterIsInvalid = false;
            mTileAdapter.reInit(records, mContext);
        }

        showDetailAdapter(mEditAdapter, x, y);
    }

    private void showDetailAdapter(Object adapter, int x, int y) {
        if (mHasEditPanel)
            y += mStatusBarHeaderView.getHeight();
        if (adapter == mEditAdapter)
            StatusBarHeaderHooks.mEditing = true;
        if (!ConfigUtils.M) {
            XposedHelpers.callMethod(mQsPanel, "showDetailAdapter", true, adapter);
        } else {
            try {
                XposedHelpers.callMethod(mQsPanel, "showDetailAdapter", true, adapter, new int[]{x, y});
            } catch (Throwable t) { // OOS3
                ClassLoader classLoader = mContext.getClassLoader();
                Class<?> classRemoteSetting = XposedHelpers.findClass(XposedHook.PACKAGE_SYSTEMUI + ".qs.RemoteSetting", classLoader);
                Object remoteSetting = Proxy.newProxyInstance(classLoader, new Class[]{classRemoteSetting}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("getSettingsIntent"))
                            return new Intent(Intent.ACTION_MAIN)
                                    .setClassName("tk.wasdennnoch.androidn_ify", SettingsActivity.class.getName())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        return null;
                    }
                });
                XposedHelpers.callMethod(mQsPanel, "showDetailAdapter", true, remoteSetting, adapter, new int[]{x, y});
            }
        }
    }

    private void createEditAdapter(ArrayList<Object> records) {
        if (mRecyclerView == null)
            createEditView(records);

        mEditAdapter = createProxy(new DetailAdapter() {
            @Override
            public int getTitle() {
                return mContext.getResources().getIdentifier("quick_settings_settings_label", "string", XposedHook.PACKAGE_SYSTEMUI);
            }

            @Override
            public Boolean getToggleState() {
                return false;
            }

            @Override
            public DetailViewAdapter createDetailView(Context context, View convertView, ViewGroup parent) {
                return mRecyclerView;
            }

            @Override
            public Intent getSettingsIntent() {
                return new Intent(Intent.ACTION_MAIN)
                        .setClassName("tk.wasdennnoch.androidn_ify", SettingsActivity.class.getName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            @Override
            public void setToggleState(boolean state) {
            }

            @Override
            public int getMetricsCategory() {
                return MetricsLogger.QS_INTENT;
            }
        });
    }

    public Object createProxy(final DetailAdapter adapter) {
        Class<?> classDetailAdapter = XposedHelpers.findClass(CLASS_DETAIL_ADAPTER, mContext.getClassLoader());
        return Proxy.newProxyInstance(mContext.getClassLoader(), new Class<?>[]{classDetailAdapter}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "getTitle":
                        return adapter.getTitle();
                    case "getToggleState":
                        return adapter.getToggleState();
                    case "createDetailView":
                        return adapter.createDetailView((Context) args[0], (View) args[1], (ViewGroup) args[2]);
                    case "getSettingsIntent":
                        return adapter.getSettingsIntent();
                    case "setToggleState":
                        adapter.setToggleState((boolean) args[0]);
                        return null;
                    case "getMetricsCategory":
                        return adapter.getMetricsCategory();
                }
                return null;
            }
        });
    }

    public DetailViewAdapter getDetailViewAdapter(View detailView) {
        if (detailView != null && detailView instanceof DetailViewAdapter) {
            return (DetailViewAdapter) detailView;
        }
        return null;
    }

    public interface DetailAdapter {
        int getTitle();

        Boolean getToggleState();

        DetailViewAdapter createDetailView(Context context, View convertView, ViewGroup parent);

        Intent getSettingsIntent();

        void setToggleState(boolean state);

        int getMetricsCategory();
    }

    public interface DetailViewAdapter {
        boolean hasRightButton();

        int getRightButtonResId();

        void handleRightButtonClick();
    }

    private void createEditView(ArrayList<Object> records) {
        mTileAdapterIsInvalid = false;
        // Init tiles list
        mTileAdapter = new TileAdapter(records, mContext, mQsPanel);
        TileTouchCallback callback = new TileTouchCallback();
        ItemTouchHelper mItemTouchHelper = new CustomItemTouchHelper(callback);
        XposedHelpers.setIntField(callback, "mCachedMaxScrollSpeed", ResourceUtils.getInstance(mContext).getDimensionPixelSize(R.dimen.lib_item_touch_helper_max_drag_scroll_per_frame));
        // With this, it's very easy to deal with drag & drop
        GridLayoutManager gridLayoutManager = new GridLayoutManager(mContext, 3);
        gridLayoutManager.setSpanSizeLookup(mTileAdapter.getSizeLookup());
        mRecyclerView = new EditRecyclerView(mContext);
        mRecyclerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRecyclerView.setAdapter(mTileAdapter);
        mRecyclerView.setLayoutManager(gridLayoutManager);
        mRecyclerView.addItemDecoration(mTileAdapter.getItemDecoration());
        /*mRecyclerView.setVerticalScrollBarEnabled(true);
        mRecyclerView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        mRecyclerView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_DEFAULT);*/
        // TODO these above have no effect and the grid isn't scrolling smoothly
        // Also a ScrollView seems to be used in the official version
        mRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mRecyclerView.canScrollVertically(1)) {
                    mRecyclerView.requestDisallowInterceptTouchEvent(true);
                }
                return false;
            }
        });
        mTileAdapter.setTileTouchCallback(callback);
        mItemTouchHelper.attachToRecyclerView(mRecyclerView);
    }

    private class EditRecyclerView extends RecyclerView implements DetailViewAdapter {

        private int mColor;

        public EditRecyclerView(Context context) {
            super(context);
            init();
        }

        public EditRecyclerView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public EditRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            init();
        }

        private void init() {
            Resources res = mContext.getResources();
            mColor = res.getColor(res.getIdentifier("system_primary_color", "color", XposedHook.PACKAGE_SYSTEMUI));
        }

        @Override
        public boolean hasRightButton() {
            return true;
        }

        @Override
        public int getRightButtonResId() {
            return R.drawable.ic_add;
        }

        @Override
        public void handleRightButtonClick() {
            Object qsTileHost = XposedHelpers.getObjectField(StatusBarHeaderHooks.mQsPanel, "mHost");
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("tk.wasdennnoch.androidn_ify", AddTileActivity.class.getName());
            intent.putExtra("color", mColor);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            try {
                XposedHelpers.callMethod(qsTileHost, "startActivityDismissingKeyguard", intent);
            } catch (Throwable t) {
                try {
                    XposedHelpers.callMethod(qsTileHost, "startSettingsActivity", intent);
                } catch (Throwable t2) {
                    XposedHook.logE(TAG, "Error starting settings activity", null);
                }
            }
        }
    }

    private class CustomItemTouchHelper extends ItemTouchHelper {

        public CustomItemTouchHelper(Callback callback) {
            super(callback);
        }

        @Override
        public void attachToRecyclerView(RecyclerView recyclerView) {
            try {
                RecyclerView oldRecyclerView = (RecyclerView) XposedHelpers.getObjectField(this, "mRecyclerView");
                if (oldRecyclerView == recyclerView) {
                    return; // nothing to do
                }
                if (oldRecyclerView != null) {
                    XposedHelpers.findMethodBestMatch(ItemTouchHelper.class, "destroyCallbacks").invoke(this);
                }
                XposedHelpers.setObjectField(this, "mRecyclerView", recyclerView);
                if (recyclerView != null) {
                    XposedHelpers.findMethodBestMatch(ItemTouchHelper.class, "setupCallbacks").invoke(this);
                }
            } catch (Throwable t) {
                XposedHook.logE(TAG, "Error attaching ItemTouchCallback to RecyclerView", t);
            }
        }
    }

    public class TileTouchCallback extends ItemTouchHelper.Callback {
        public TileAdapter.TileViewHolder mCurrentDrag;

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END;
            if (viewHolder.getItemViewType() == 1) {
                dragFlags = 0;
            }
            return makeMovementFlags(dragFlags, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return mTileAdapter.onItemMove(viewHolder.getAdapterPosition(),
                    target.getAdapterPosition());
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (mCurrentDrag != null) {
                mCurrentDrag.stopDrag();
                mCurrentDrag = null;
            }
            if (viewHolder != null) {
                mCurrentDrag = (TileAdapter.TileViewHolder) viewHolder;
                mCurrentDrag.startDrag();
            }
            try {
                mTileAdapter.notifyItemChanged(mTileAdapter.mDividerIndex);
            } catch (Throwable ignore) {

            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            ((TileAdapter.TileViewHolder) viewHolder).stopDrag();
            super.clearView(recyclerView, viewHolder);
        }
    }

    public static class DetailFrameLayout extends FrameLayout implements DetailViewAdapter {
        private DetailViewAdapter mAdapter;

        public DetailFrameLayout(Context context, DetailViewAdapter adapter) {
            super(context);
            mAdapter = adapter;
        }

        @Override
        public boolean hasRightButton() {
            return mAdapter.hasRightButton();
        }

        @Override
        public int getRightButtonResId() {
            return mAdapter.getRightButtonResId();
        }

        @Override
        public void handleRightButtonClick() {
            mAdapter.handleRightButtonClick();
        }
    }

}
