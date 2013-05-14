package com.lq.fragment;

import java.io.File;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore.Audio.Media;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.lq.activity.MainContentActivity;
import com.lq.activity.R;
import com.lq.adapter.TrackAdapter;
import com.lq.entity.ArtistInfo;
import com.lq.entity.FolderInfo;
import com.lq.entity.MusicItem;
import com.lq.listener.OnPlaybackStateChangeListener;
import com.lq.loader.MusicRetrieveLoader;
import com.lq.service.MusicService;
import com.lq.service.MusicService.MusicPlaybackLocalBinder;
import com.lq.util.GlobalConstant;

/**
 * 读取并显示设备外存上的音乐文件
 * 
 * @author lq
 * */
public class TrackBrowserFragment extends Fragment implements
		LoaderManager.LoaderCallbacks<List<MusicItem>>, OnItemClickListener {
	// 调试用的标记
	private final String TAG = this.getClass().getSimpleName();

	private static final int MUSIC_RETRIEVE_LOADER = 0;

	private String mSortOrder = Media.DEFAULT_SORT_ORDER;

	private Bundle mCurrentPlayInfo = null;

	private boolean mHasNewData = false;

	private MainContentActivity mActivity = null;

	/** 显示本地音乐的列表 */
	private ListView mView_ListView = null;

	private ImageView mView_MenuNavigation = null;
	private ImageView mView_GoToPlayer = null;
	private ImageView mView_MoreFunctions = null;
	private TextView mView_Title = null;
	private ViewGroup mView_Top_Container = null;

	private PopupMenu mOverflowPopupMenu = null;

	/** 用来绑定数据至ListView的适配器 */
	private TrackAdapter mAdapter = null;

	private ArtistInfo mArtistInfo = null;
	private FolderInfo mFolderInfo = null;

	private MusicPlaybackLocalBinder mMusicServiceBinder = null;

	/** 与Service连接时交互的类 */
	private ServiceConnection mServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.i(TAG, "onServiceConnected");
			mMusicServiceBinder = (MusicPlaybackLocalBinder) service;
			mMusicServiceBinder
					.registerOnPlaybackStateChangeListener(mOnPlaybackStateChangeListener);
			mCurrentPlayInfo = mMusicServiceBinder.getCurrentPlayInfo();
		}

		// 与服务端连接异常丢失时才调用，调用unBindService不调用此方法哎
		public void onServiceDisconnected(ComponentName className) {
		}
	};

	@Override
	public void onAttach(Activity activity) {
		Log.i(TAG, "onAttach");
		super.onAttach(activity);
		if (activity instanceof MainContentActivity) {
			mActivity = (MainContentActivity) activity;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);
	}

	/** 在此加载一个ListView，可以使用自定义的ListView */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		Log.i(TAG, "onCreateView");
		View rootView = inflater.inflate(R.layout.list_track, container, false);
		mView_ListView = (ListView) rootView
				.findViewById(R.id.listview_local_music);
		mView_MenuNavigation = (ImageView) rootView
				.findViewById(R.id.menu_navigation);
		mView_Title = (TextView) rootView.findViewById(R.id.title);
		mView_MoreFunctions = (ImageView) rootView
				.findViewById(R.id.more_functions);
		mView_GoToPlayer = (ImageView) rootView
				.findViewById(R.id.switch_to_player);
		mView_Top_Container = (ViewGroup) rootView
				.findViewById(R.id.top_of_local_music);

		mOverflowPopupMenu = new PopupMenu(getActivity(), mView_MoreFunctions);

		Bundle args = getArguments();
		if (args != null) {
			if (args.getString(GlobalConstant.PARENT).equals(
					LocalMusicFragment.class.getSimpleName())) {
				mOverflowPopupMenu.getMenuInflater().inflate(
						R.menu.popup_local_music_list,
						mOverflowPopupMenu.getMenu());
			} else {
				mOverflowPopupMenu.getMenuInflater().inflate(
						R.menu.popup_track_list, mOverflowPopupMenu.getMenu());
			}
		}
		return rootView;
	}

	/** 延迟ListView的设置到Activity创建时，为ListView绑定数据适配器 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		Log.i(TAG, "onActivityCreated");
		super.onActivityCreated(savedInstanceState);

		initViewsSetting();

		// 如果有谁传递数据过来了，就设置一下
		Bundle args = getArguments();
		if (args != null) {
			if (args.getString(GlobalConstant.PARENT).equals(
					ArtistBrowserFragment.class.getSimpleName())) {
				// 如果是从歌手列表里启动的
				mArtistInfo = args.getParcelable(ArtistInfo.class
						.getSimpleName());
				if (mArtistInfo != null) {
					// 更新标题
					if (!mArtistInfo.getArtistName().equals("<unknown>")) {
						mView_Title.setText(mArtistInfo.getArtistName() + "("
								+ mArtistInfo.getNumberOfTracks() + ")");
					} else {
						mView_Title.setText(getResources().getString(
								R.string.unknown_artist)
								+ "(" + mArtistInfo.getNumberOfTracks() + ")");
					}
					setTitleLeftDrawable();
				}
			} else if (args.getString(GlobalConstant.PARENT).equals(
					FolderBrowserFragment.class.getSimpleName())) {
				// 如果是从文件夹列表里启动的
				mFolderInfo = args.getParcelable(FolderInfo.class
						.getSimpleName());
				if (mFolderInfo != null) {
					// 更新标题
					mView_Title.setText(mFolderInfo.getFolderName() + "("
							+ mFolderInfo.getNumOfTracks() + ")");
					setTitleLeftDrawable();
				}
			}
		}
		// 初始化一个装载器，根据第一个参数，要么连接一个已存在的装载器，要么以此ID创建一个新的装载器
		getLoaderManager().initLoader(MUSIC_RETRIEVE_LOADER, null, this);

	}

	@Override
	public void onStart() {
		Log.i(TAG, "onStart");
		super.onStart();
		// 在Fragment可见时绑定服务 ，以使服务可以发送消息过来
		getActivity().bindService(
				new Intent(getActivity(), MusicService.class),
				mServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();

	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		Log.i(TAG, "onSaveInstanceState");
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onStop() {
		Log.i(TAG, "onStop");
		super.onStop();

		// Fragment不可见时，无需更新UI，取消服务绑定
		mActivity.unbindService(mServiceConnection);
	}

	@Override
	public void onDetach() {
		Log.i(TAG, "onDetach");
		super.onDetach();
		mActivity = null;
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		super.onDestroy();
	}

	private void initViewsSetting() {
		// 创建一个空的适配器，用来显示加载的数据，适配器内容稍后由Loader填充
		mAdapter = new TrackAdapter(getActivity());
		// 为ListView绑定数据适配器
		mView_ListView.setAdapter(mAdapter);
		// 为ListView的条目绑定一个点击事件监听
		mView_ListView.setOnItemClickListener(this);
		mView_ListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		mView_ListView
				.setMultiChoiceModeListener(new MultiChoiceModeListener() {

					@Override
					public boolean onCreateActionMode(ActionMode mode, Menu menu) {
						mView_Top_Container.setVisibility(View.GONE);
						// mActivity.forbidSlide();
						MenuInflater inflater = mActivity.getMenuInflater();
						inflater.inflate(R.menu.main_content, menu);
						mode.setTitle("Select Items");
						return true;
					}

					@Override
					public boolean onPrepareActionMode(ActionMode mode,
							Menu menu) {
						return true;
					}

					@Override
					public boolean onActionItemClicked(ActionMode mode,
							MenuItem item) {
						switch (item.getItemId()) {
						case R.id.go_to_play:
							Toast.makeText(mActivity, "Clicked Action Item",
									Toast.LENGTH_SHORT).show();
							mode.finish();
							break;
						}
						return true;
					}

					@Override
					public void onDestroyActionMode(ActionMode mode) {
						mView_Top_Container.setVisibility(View.VISIBLE);
					}

					@Override
					public void onItemCheckedStateChanged(ActionMode mode,
							int position, long id, boolean checked) {

					}

				});

		mView_Title.setClickable(false);
		mView_Title.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getFragmentManager().popBackStackImmediate();
			}
		});
		mView_GoToPlayer.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mActivity.switchToPlayer();
			}
		});
		mOverflowPopupMenu
				.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						switch (item.getItemId()) {
						case R.id.sort_by_music_name:
							mSortOrder = Media.TITLE_KEY;
							getLoaderManager().restartLoader(
									MUSIC_RETRIEVE_LOADER, null,
									TrackBrowserFragment.this);
							break;
						case R.id.sort_by_last_modify_time:
							mSortOrder = Media.DATE_MODIFIED;
							getLoaderManager().restartLoader(
									MUSIC_RETRIEVE_LOADER, null,
									TrackBrowserFragment.this);
							break;
						case R.id.classify_by_artist:
							if (null != getParentFragment()
									&& getParentFragment() instanceof LocalMusicFragment) {
								getFragmentManager()
										.beginTransaction()
										.replace(
												R.id.frame_of_local_music,
												Fragment.instantiate(
														getActivity(),
														ArtistBrowserFragment.class
																.getName(),
														null))
										.addToBackStack(null).commit();
							}
							break;
						case R.id.classify_by_folder:
							if (null != getParentFragment()
									&& getParentFragment() instanceof LocalMusicFragment) {
								getFragmentManager()
										.beginTransaction()
										.replace(
												R.id.frame_of_local_music,
												Fragment.instantiate(
														getActivity(),
														FolderBrowserFragment.class
																.getName(),
														null))
										.addToBackStack(null).commit();
							}
							break;
						case R.id.sort_by_play_frequency:
							break;
						default:
							break;
						}
						return true;
					}
				});

		mView_MenuNavigation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mActivity.getSlidingMenu().showMenu();
			}
		});

		mView_MoreFunctions.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				mOverflowPopupMenu.show();
			}
		});

	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		if (mHasNewData && mMusicServiceBinder != null) {
			mMusicServiceBinder.setCurrentPlayList(mAdapter.getData());
		}
		mHasNewData = false;
		Intent intent = new Intent(MusicService.ACTION_PLAY);
		intent.putExtra(GlobalConstant.REQUEST_PLAY_ID,
				mAdapter.getItem(position).getId());
		intent.putExtra(GlobalConstant.CLICK_ITEM_IN_LIST, 1);
		mActivity.startService(intent);
		mActivity.switchToPlayer();
	}

	/** 在装载器需要被创建时执行此方法，这里只有一个装载器，所以我们不必关心装载器的ID */
	@Override
	public Loader<List<MusicItem>> onCreateLoader(int id, Bundle args) {
		Log.i(TAG, "onCreateLoader");

		// 查询语句：检索出.mp3为后缀名，时长大于1分钟，文件大小大于1MB的媒体文件
		StringBuffer select = new StringBuffer("(" + Media.DATA
				+ " like'%.mp3' or " + Media.DATA + " like'%.wma') and "
				+ Media.DURATION + " > " + 1000 * 60 * 1 + " and " + Media.SIZE
				+ " > " + 1024);

		if (mArtistInfo != null) {
			select.append(" and " + Media.ARTIST + " = '"
					+ mArtistInfo.getArtistName() + "'");
		} else if (mFolderInfo != null) {
			select.append(" and " + Media.DATA + " like '"
					+ mFolderInfo.getFolderPath() + File.separator + "%'");
		}

		MusicRetrieveLoader loader = new MusicRetrieveLoader(getActivity(),
				select.toString(), null, mSortOrder);

		if (mFolderInfo != null) {
			loader.setFolderFilterPattern(mFolderInfo.getFolderPath());
		}

		// 创建并返回一个Loader
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<List<MusicItem>> loader,
			List<MusicItem> data) {
		Log.i(TAG, "onLoadFinished");
		mHasNewData = true;

		// TODO SD卡拔出时，没有处理
		mAdapter.setData(data);

		if (getArguments() != null
				&& getArguments().getString(GlobalConstant.PARENT).equals(
						LocalMusicFragment.class.getSimpleName())) {
			mView_Title.setText(getResources().getString(R.string.local_music)
					+ "(" + data.size() + ")");
		}

		if (mCurrentPlayInfo != null) {
			initCurrentPlayInfo(mCurrentPlayInfo);
		}
	}

	/** 此方法在提供给onLoadFinished()最后的一个游标准备关闭时调用，我们要确保不再使用它 */
	@Override
	public void onLoaderReset(Loader<List<MusicItem>> loader) {
		Log.i(TAG, "onLoaderReset");
		mAdapter.setData(null);
	}

	/** 初始化当前播放信息 */
	private void initCurrentPlayInfo(Bundle bundle) {
		MusicItem playingSong = bundle
				.getParcelable(GlobalConstant.PLAYING_MUSIC_ITEM);

		if (playingSong != null) {
			mAdapter.setSpecifiedIndicator(MusicService.seekPosInListById(
					mAdapter.getData(), playingSong.getId()));
		} else {
			mAdapter.setSpecifiedIndicator(-1);
		}

	}

	private void setTitleLeftDrawable() {
		mView_Title.setClickable(true);
		Drawable title_drawable = getResources().getDrawable(
				R.drawable.btn_titile_back);
		title_drawable.setBounds(0, 0, title_drawable.getIntrinsicWidth(),
				title_drawable.getIntrinsicHeight());
		mView_Title.setCompoundDrawables(title_drawable, null, null, null);
		mView_Title.setBackgroundResource(R.drawable.button_backround_light);
	}

	private OnPlaybackStateChangeListener mOnPlaybackStateChangeListener = new OnPlaybackStateChangeListener() {

		@Override
		public void onMusicPlayed() {

		}

		@Override
		public void onMusicPaused() {

		}

		@Override
		public void onMusicStopped() {

		}

		@Override
		public void onPlayNewSong(MusicItem playingSong) {
			mAdapter.setSpecifiedIndicator(MusicService.seekPosInListById(
					mAdapter.getData(), playingSong.getId()));
		}

		@Override
		public void onPlayModeChanged(int playMode) {

		}

		@Override
		public void onPlayProgressUpdate(int currentMillis) {

		}

	};
}