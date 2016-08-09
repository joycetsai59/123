package com.smc.tw.waltz;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import com.crashlytics.android.Crashlytics;
import com.google.GCM.GcmIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.parse.ParseInstallation;
import com.parse.ParsePush;
import com.smc.tw.waltz.event.device.DeviceConnectedEvent;
import com.smc.tw.waltz.event.device.DeviceConnectionFailEvent;
import com.smc.tw.waltz.event.device.DeviceUninitializedEvent;
import com.smc.tw.waltz.event.device.RefreshHTUIInfoEvent;
import com.smc.tw.waltz.event.device.RefreshInfoPageEvent;
import com.smc.tw.waltz.event.device.RefreshMediaPageEvent;
import com.smc.tw.waltz.event.device.RefreshMotionWindowViewEvent;
import com.smc.tw.waltz.event.device.StreamMuteChangedEvent;
import com.smc.tw.waltz.event.device.WPSStartParingEvent;
import com.smc.tw.waltz.fragment.DeviceInfoFragment;
import com.smc.tw.waltz.fragment.DeviceMotionFragment;
import com.smc.tw.waltz.fragment.DevicePagerFragment;
import com.smc.tw.waltz.fragment.DeviceVideoListFragment;
import com.smc.tw.waltz.fragment.DropboxFragment;
import com.smc.tw.waltz.fragment.EventHistoryFragment;
import com.smc.tw.waltz.fragment.HelpFragment;
import com.smc.tw.waltz.management.device.WaltzDevice;
import com.smc.tw.waltz.fragment.DeviceListFragment;
import com.smc.tw.waltz.fragment.DeviceMediaFragment;
import com.smc.tw.waltz.fragment.SensorGridFragment;
import com.smc.tw.waltz.fragment.AboutFragment;
import com.smc.tw.waltz.event.device.AccessCodeDoneEvent;
import com.smc.tw.waltz.event.device.AccessCodeFailEvent;
import com.smc.tw.waltz.event.device.DeviceInitializedEvent;
import com.smc.tw.waltz.management.user.UserManager;
import com.smc.tw.waltz.fragment.UserListFragment; // Add Device's member list
import com.smc.tw.waltz.tutk.TutkConnectionChangedEvent;
import com.smc.tw.waltz.tutk.TutkErrorCode;
import com.smc.tw.waltz.util.G711UCodec;
import com.smc.tw.waltz.view.PinEditText;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.JsonReader;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, DevicePagerFragment.OnFullscreenListener, DropboxFragment.OnLoginListener, DeviceListFragment.OnSelectionListener, DeviceMediaFragment.OnMediaListener, DeviceInfoFragment.OnInfoListener, UserListFragment.OnOwnerChangeListener, DeviceMediaFragment.OnDeviceInfoListener, DeviceMotionFragment.OnDeviceInfoListener, SensorGridFragment.OnDeviceInfoListener, DeviceVideoListFragment.OnDeviceInfoListener, EventHistoryFragment.OnDeviceInfoListener, UserListFragment.OnDeviceInfoListener //, AudioRecord.OnRecordPositionUpdateListener
{
	private final static String TAG = "MainActivity";
	private final static boolean DEBUG = false;

	private boolean mIsStopped = false;
	private boolean mIsInstanceStateSaved = false;
	
	private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
	
	private static final int RESULT_ADD_WALTZ_ONE = 1;
	private static final int RESULT_CONNECT_WIFI = 5;
	private static final int RESULT_DROPBOX_LOGIN = 6;

	private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
	private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 2;
	
	private boolean mIsOriginalStreamMuted = false;

	private SharedPreferences mPreferences;

	private AudioManager mAudioManager;
	private PowerManager mPowerManager;
	private PowerManager.WakeLock mWakeLock;
	
	private FragmentManager mFragmentManager;
	
	private int mCurrentSelectedPosition = 0;
	
	private Toolbar mToolbar;
	private DrawerLayout mDrawerLayout;
	private NavigationView mNavigationView;
	private ActionBarDrawerToggle mDrawerToggle;
	private String mTitle;
	
	private ProgressDialog mProgressDialog;

	private boolean mShowDropbox = false;

	private DevicePagerFragment mDevicePagerFragment;
	private DeviceMediaFragment mDeviceMediaFragment;
	private DeviceMotionFragment mDeviceMotionFragment;
	private DropboxFragment mDropboxFragment;
	private DeviceListFragment mDeviceListFragment;
	private DeviceInfoFragment mDeviceInfoFragment;
	private HelpFragment mHelpFragment;
	private AboutFragment mAboutFragment;
	private UserListFragment mUserListFragment; // Add Device's member list
	private SensorGridFragment mSensorGridFragment;
	private DeviceVideoListFragment mDeviceVideoListFragment;
	private EventHistoryFragment mEventHistoryFragment;

	private ArrayList<String> mNotifyChannelList;
	
	private WaltzDevice mPermissionDevice;
	private int mCurrentDeviceIndex = -1;
	
	// Alert Dialog
	private AlertDialog mInitializationFailDialog;
	private AlertDialog mInputAccessCodeDialog;
	private AlertDialog mNewAccessCodeDialog;
	private AlertDialog mWPSDialog;
	
	// Bi-Audio
	private static Thread mAudioThread;
	private static boolean mIsAudioRecording = false;
	private static G711UCodec mAudioCodec = new G711UCodec();

	private static int mAudioRecordBufferSize;
	private static AudioRecord mAudioRecord;
	
	// Access Code
	private static String mTempAccessCode;

	private boolean mForceSignIn = true;

	// Handler
	private Handler mStreamMuteChangedHandler = new Handler();
	private Handler mStreamQualityChangedHandler = new Handler();
		
	private final int STREAM_CHANGED_DELAY = 2000;

	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
	private BroadcastReceiver mRegistrationBroadcastReceiver;

	// WPS
	private Handler mPrepareWPSParingHandler = new Handler();
	private Handler mWPSParingTimeLeftHandler = new Handler();
	private final long WPS_PARING_TIME = 115*1000; // Total 120 sec - prepare buffer 5 sec
	private final int WPS_PARING_BUFFER = 5*1000;
	private int mWPSParingTimeLeft;
	private int mPrepareWPSParingCount;
	private int mWPSParingCount;
	private TextView mWPSParingText;
	private boolean mIsHasSeenWPSParingAlert = false;
	private boolean mIsEndPrepareParing = false;
	private WaltzDevice mEventDevice = null;

	// Tutk
	private String mTutkDeviceName;
	private String mTutkDeviceUid;
	private int mTutkError = TutkErrorCode.TUTK_ERROR_NONE;

	private Handler mTutkConnectedHandler = new Handler();
	private Handler mTutkConnectionFailHandler = new Handler();
	
	private Handler mConnectionExceedHandler = new Handler();
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		if(DEBUG)
			Log.d(TAG, "onCreate");
			
		overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);

		super.onCreate(savedInstanceState);

		Fabric.with(this, new Crashlytics());

		setContentView(R.layout.activity_main);

		mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);

		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		mFragmentManager = getSupportFragmentManager();
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		mAudioRecordBufferSize = 5600;//AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*10;
		mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mAudioRecordBufferSize);
	
		mNotifyChannelList = new ArrayList<String>();

		setupLayout();
		
		if(savedInstanceState!=null)
		{
			mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
		}

//		mRegistrationBroadcastReceiver = new BroadcastReceiver() {
//			@Override
//			public void onReceive(Context context, Intent intent) {
//
//				// checking for type intent filter
//				if (intent.getAction().equals(MainApplication.REGISTRATION_COMPLETE)) {
//					// gcm successfully registered
//					// now subscribe to `global` topic to receive app wide notifications
//					String token = intent.getStringExtra("token");
//
//					//Toast.makeText(getApplicationContext(), "GCM registration token: " + token, Toast.LENGTH_LONG).show();
//
//				} else if (intent.getAction().equals(MainApplication.SENT_TOKEN_TO_SERVER)) {
//					// gcm registration id is stored in our server's MySQL
//
//					Toast.makeText(getApplicationContext(), "GCM registration token is stored in server!", Toast.LENGTH_LONG).show();
//
//				} else if (intent.getAction().equals(MainApplication.PUSH_NOTIFICATION)) {
//					// new push notification is received
//
//					Toast.makeText(getApplicationContext(), "Push notification is received!", Toast.LENGTH_LONG).show();
//				}
//			}
//		};
//
		if (checkPlayServices()) {
			registerGCM();
		}

	}

	@Override
	public void onResume()
	{
		if(DEBUG)
			Log.d(TAG, "onResume");

		super.onResume();
		
		if(mFragmentManager.getBackStackEntryCount()>0)
		{
			if(mDeviceInfoFragment.isHidden())
			{
				mDevicePagerFragment.clearAllFragment();
				mDevicePagerFragment.notifyDataSetChanged();
			}

			try
			{
				mFragmentManager.popBackStackImmediate();
			}
			catch(IllegalStateException e)
			{
			}
		}

		for(int i=0; i<MainApplication.getWaltzDeviceNumber(); i++)
		{
			WaltzDevice device = MainApplication.getWaltzDevice(i);
	
			if(device.getSerial() != null)
				mNotifyChannelList.add("WALTZ_" + device.getSerial());
		}

		ParseInstallation installation = ParseInstallation.getCurrentInstallation();
		installation.remove("channels");
		installation.addAllUnique("channels", mNotifyChannelList);
		installation.saveInBackground();

		checkSignIn();

		resetLayout();
		
//		// register GCM registration complete receiver
//		LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
//				new IntentFilter(MainApplication.REGISTRATION_COMPLETE));
//
//		// register new push message receiver
//		// by doing this, the activity will be notified each time a new message arrives
//		LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
//				new IntentFilter(MainApplication.PUSH_NOTIFICATION));

		unsubscribeAllGcmChannel();
		subscribeGcmChannels(mNotifyChannelList);
	}

	@Override
	public void onPause()
	{
		if(DEBUG)
			Log.d(TAG, "onPause");
			
//		LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		if(DEBUG)
			Log.d(TAG, "onDestroy");
			
		MainApplication.saveDeviceList();

		mAudioRecord.release();
		
		super.onDestroy();
		
		MainApplication.leakWatch(this);
	}

	@Override
	public void onStart()
	{
		if(DEBUG)
			Log.d(TAG, "onStart");

		super.onStart();
		
		mIsStopped = false;
		mIsInstanceStateSaved = false;

		EventBus.getDefault().register(this);
	}

	@Override
	public void onStop()
	{
		if(DEBUG)
			Log.d(TAG, "onStop");

		EventBus.getDefault().unregister(this);

		mIsStopped = true;

		/*
		if(mDevicePagerFragment.isMotionFullscreen())
		{
			onMotionFullscreen(false);
		}
		else if(mDevicePagerFragment.isMediaFullscreen())
		{
			onCameraFullscreen(false);
		}
		else if(mDevicePagerFragment.isVideoListFullscreen())
		{
			onVideoListFullscreen(false);
		}
		*/

		super.onStop();
	}

	@Override
	protected void onResumeFragments()
	{
		if(DEBUG)
			Log.d(TAG, "onResumeFragments");

		super.onResumeFragments();

		mIsInstanceStateSaved = false;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		if(DEBUG)
			Log.d(TAG, "onSaveInstanceState b:" + outState);

		mIsInstanceStateSaved = true;

		super.onSaveInstanceState(outState);
		
		outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		if(DEBUG)
			Log.d(TAG, "onRestoreInstanceState b:" + savedInstanceState);

		super.onRestoreInstanceState(savedInstanceState);
		
		mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION, 0);
		Menu menu = mNavigationView.getMenu();
		menu.getItem(mCurrentSelectedPosition).setChecked(true);
	}

	@Override
	public void onBackPressed()
	{
		if(DEBUG)
			Log.d(TAG, "onBackPressed c:" + mFragmentManager.getBackStackEntryCount());

		if(mDevicePagerFragment.isMotionFullscreen())
		{
			onMotionFullscreen(false);
		}
		else if(mDevicePagerFragment.isMediaFullscreen())
		{
			onCameraFullscreen(false);
		}
		else if(mDevicePagerFragment.isVideoListFullscreen())
		{
			onVideoListFullscreen(false);
		}
		else if(mFragmentManager.getBackStackEntryCount()>0)
		{
			if(mDeviceInfoFragment.isHidden())
			{
				mDevicePagerFragment.clearAllFragment();
				mDevicePagerFragment.notifyDataSetChanged();
			}
            else
                mDeviceMediaFragment.refreshStreamWithoutProgress();

			try
			{
				mFragmentManager.popBackStackImmediate();
			}
			catch(IllegalStateException e)
			{
			}
		}
		else if(!mDevicePagerFragment.isHidden())
		{
			showDeviceList();
		}
		else
		{
			showQuitDialog();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onKeyDown code:" + keyCode);

		switch(keyCode)
		{
			case KeyEvent.KEYCODE_VOLUME_UP:
			{
				mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);

				return true;
			}

			case KeyEvent.KEYCODE_VOLUME_DOWN:
			{
				mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);

				return true;
			}

			default:
				break;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem menuItem)
	{
		if(DEBUG)
			Log.d(TAG, "onNavigationItemSelected m:" + menuItem);

		menuItem.setChecked(true);
		
		int id = menuItem.getItemId();
		
		if(DEBUG)
			Log.d(TAG, "onNavigationItemSelected i:" + id);

		if(DEBUG)
			Log.d(TAG, "onNavigationItemSelected c:" + mFragmentManager.getBackStackEntryCount());

		while(mFragmentManager.getBackStackEntryCount()>0)
		{
			try
			{
				mFragmentManager.popBackStackImmediate();
			}
			catch(IllegalStateException e)
			{
			}
		}

		mDevicePagerFragment.clearAllFragment();
		mDevicePagerFragment.notifyDataSetChanged();

		switch(id)
		{
			case R.id.navigation_device_list:
			{
				showDeviceList();
				
				break;
			}
			
			case R.id.navigation_dropbox:
			{
				showDropboxAssociation();
				
				break;
			}

			case R.id.navigation_signin:
			{
				mForceSignIn = true;
				SignIn();
				
				break;
			}

			case R.id.navigation_signout:
			{
				showSignOutDialog();
				
				break;
			}

			case R.id.navigation_help:
			{
				showHelp();
				
				break;
			}
			
			case R.id.navigation_about:
			{
				showAbout();
				
				break;
			}
			
			default:
				break;
		}
		
		mDrawerLayout.closeDrawer(GravityCompat.START);
		
		return true;
	}

	@Override
	public void onConfigurationChanged(Configuration config)
	{
		if(DEBUG)
			Log.d(TAG, "onConfigurationChanged c:" + config);

		if(mIsStopped || mIsInstanceStateSaved)
		{
			super.onConfigurationChanged(config);
			return;
		}

		resetLayoutOrientation();

		super.onConfigurationChanged(config);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if(DEBUG)
			Log.d(TAG, "onActivityResult: " + requestCode + ", " + resultCode + " d:" + data);
			
		if(resultCode!=RESULT_OK)
			return;

		switch(requestCode)
		{
			case RESULT_CONNECT_WIFI:
			{
				onRefreshDevice();
				
				break;
			}

			case RESULT_DROPBOX_LOGIN:
			{
				showDropboxAssociation();
				
				break;
			}

			default:
				break;
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
	{
		if(DEBUG)
			Log.d(TAG, "onRequestPermissionsResult c:" + requestCode + " p:" + permissions + " r:" + grantResults);
		
		switch(requestCode)
		{
			case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
			{
				if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
				{
					if(DEBUG)
						Log.d(TAG, "onRequestPermissionsResult granted");

					onTakeSnapshot(mPermissionDevice);
					
					mPermissionDevice = null;
				}
				else
				{
					if(DEBUG)
						Log.d(TAG, "onRequestPermissionsResult denied");
				}
				
				break;
			}
			
			case PERMISSIONS_REQUEST_RECORD_AUDIO:
			{
				if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
				{
					if(DEBUG)
						Log.d(TAG, "onRequestPermissionsResult granted");

					onTalk(mPermissionDevice, true);
					
					mPermissionDevice = null;
				}
				else
				{
					if(DEBUG)
						Log.d(TAG, "onRequestPermissionsResult denied");
				}
				
				break;
			}

			default:
				break;
		}
	}

	private void checkSignIn()
	{
		if(DEBUG)
			Log.d(TAG, "checkSignIn");

		MainApplication.loadAccountData();
		
		UserManager userManager = MainApplication.getUserManager();

		String name = userManager.getUsername();
		String email = userManager.getEmail();
		String password = userManager.getPassword();
		
		if(name==null || password==null || name.length()<=0 || password.length()<=0)
			return;
		
		SignIn();
	}
	
	private void SignIn()
	{
		if(DEBUG)
			Log.d(TAG, "SignIn");

		if( !mForceSignIn)// && MainApplication.isUserSignedIn() )
			return;
			
		mForceSignIn = false;

		try
		{
			Intent intent = new Intent(MainActivity.this, UserActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			
			startActivity(intent);
		}
		catch(ActivityNotFoundException e)
		{
		}
	}
	
	private void signOut()
	{
		if(DEBUG)
			Log.d(TAG, "signOut");

		if(!MainApplication.isUserSignedIn())
			return;
		
		UserManager userManager = MainApplication.getUserManager();
		
		userManager.localSignOut();

		resetLayout();
		
		MainApplication.loadLocalDeviceList();

		MainApplication.removeAccountData();
		
		mToolbar.setTitle(R.string.title_device);

		if(mDeviceListFragment!=null)
			mDeviceListFragment.notifyDataSetChanged();
			
		showDeviceList();
	}
	
	private void hideToolbar()
	{
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		mToolbar.animate().translationY(-mToolbar.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
		
		mToolbar.setVisibility(View.GONE);
		
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
	}
	
	private void showToolbar()
	{
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		mToolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
		
		mToolbar.setVisibility(View.VISIBLE);
		
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	}

	@Override
	public void onLoginDropbox()
	{
		if(DEBUG)
			Log.d(TAG, "onLoginDropbox");
			
		MainApplication.loginDropbox(this, RESULT_DROPBOX_LOGIN);
	}

	@Override
	public void onLogoutDropbox()
	{
		if(DEBUG)
			Log.d(TAG, "onLogoutDropbox");
			
		MainApplication.logoutDropbox();
	}

	@Override
	public void onOwnerChange()
	{
		if(DEBUG)
			Log.d(TAG, "onOwnerChange");
		
		if(mCurrentDeviceIndex<0 || mCurrentDeviceIndex>=MainApplication.getWaltzDeviceNumber())
			return;
			
		WaltzDevice currentDevice = MainApplication.getWaltzDevice(mCurrentDeviceIndex);

		if(currentDevice==null)
			return;

		currentDevice.setScope(UserManager.SCOPE_USER);
		
		showDeviceList();
	}
	
	@Override
	public void onAddDevice()
	{
		if(DEBUG)
			Log.d(TAG, "onAddDevice");
			
		try
		{
			Intent intent = new Intent(getApplicationContext(), AddWaltzOneActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

			intent.putExtra(AddWaltzOneActivity.FORCE_ADD, true);
				
			startActivity(intent);
		}
		catch(ActivityNotFoundException e)
		{
		}
	}
	
	@Override
	public void onRefreshDevice()
	{
		if(DEBUG)
			Log.d(TAG, "onRefreshDevice");
		
		if(mDeviceListFragment!=null)
			mDeviceListFragment.updateAllItem();
	}
	
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onDeviceInitialized(DeviceInitializedEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onDeviceInitialized e:" + event);

		WaltzDevice device = event.getDevice();
		
		if(device==null)
			return;

		if(mDeviceListFragment!=null)
			mDeviceListFragment.updateAllItem();
					
		if(mCurrentDeviceIndex<0 || mCurrentDeviceIndex>=MainApplication.getWaltzDeviceNumber())
			return;
			
		WaltzDevice currentDevice = MainApplication.getWaltzDevice(mCurrentDeviceIndex);

		if(currentDevice==null)
			return;
			
		if(!device.equals(currentDevice))
			return;

		if(mProgressDialog!=null)
		{
			mProgressDialog.setMessage(getString(R.string.main_connecting) + " " + device.getName() + "...");
			mProgressDialog.show();
		}
	}
	
	private void onInternalDeviceConnected(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "onInternalDeviceConnected d:" + device);

		if(mProgressDialog!=null && mProgressDialog.isShowing())
			mProgressDialog.dismiss();
		
		if(device==null)
			return;
		
		if(!device.isAccessCodeCheckedDone())
			return;
			
		if(DEBUG)
			Log.d(TAG, "onInternalDeviceConnected 7");

		if(!device.isAccessCodeCheckedSuccess())
			return;
			
		//device.checkValidationAgain();
			
		if(DEBUG)
			Log.d(TAG, "onInternalDeviceConnected s:" + MainApplication.isUserSignedIn() + " o:" + device.isOwnerScope());

		if(MainApplication.isUserSignedIn() && device.isOwnerScope())
		{
			mDevicePagerFragment.clearAllFragment();
			
			mDevicePagerFragment.addFragment(mDeviceMediaFragment, R.drawable.tab_selector_camera, R.string.tab_camera);
			mDevicePagerFragment.addFragment(mDeviceMotionFragment, R.drawable.tab_selector_motion, R.string.tab_motion);
			mDevicePagerFragment.addFragment(mSensorGridFragment, R.drawable.tab_selector_sensor, R.string.tab_sensor);
			mDevicePagerFragment.addFragment(mDeviceVideoListFragment, R.drawable.tab_selector_video, R.string.tab_video);
			mDevicePagerFragment.addFragment(mEventHistoryFragment, R.drawable.tab_selector_history, R.string.tab_history);
			//mDevicePagerFragment.addFragment(mDeviceInfoFragment, R.drawable.tab_selector_info, R.string.tab_about);
			mDevicePagerFragment.addFragment(mUserListFragment, R.drawable.tab_user, R.string.tab_user);
		}
		else
		{
			mDevicePagerFragment.clearAllFragment();
			
			mDevicePagerFragment.addFragment(mDeviceMediaFragment, R.drawable.tab_selector_camera, R.string.tab_camera);
			mDevicePagerFragment.addFragment(mDeviceMotionFragment, R.drawable.tab_selector_motion, R.string.tab_motion);
			mDevicePagerFragment.addFragment(mSensorGridFragment, R.drawable.tab_selector_sensor, R.string.tab_sensor);
			mDevicePagerFragment.addFragment(mDeviceVideoListFragment, R.drawable.tab_selector_video, R.string.tab_video);
			mDevicePagerFragment.addFragment(mEventHistoryFragment, R.drawable.tab_selector_history, R.string.tab_history);
			//mDevicePagerFragment.addFragment(mDeviceInfoFragment, R.drawable.tab_selector_info, R.string.tab_about);
		}
		
		mDevicePagerFragment.setDevice(device);
		mDevicePagerFragment.notifyDataSetChanged();

		File snapshotFile = MainApplication.getSnapshotPath(device);
		
		//if(!snapshotFile.exists())
		//{
			device.takeCameraSnapshot(device.getDeviceId());
		//}

		String streamingUrl = device.getCurrentRtspStreamUrl();
		
		if(streamingUrl==null)
			return;
			
		hideAllFragments();

		if(mIsStopped || mIsInstanceStateSaved)
			return;

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		fragmentTransaction.show(mDevicePagerFragment);
		fragmentTransaction.hide(mDeviceListFragment);

		fragmentTransaction.addToBackStack(null);

		fragmentTransaction.commit();
		
		//mDevicePagerFragment.setDevice(device);

		UserManager userManager = MainApplication.getUserManager();
        /*
		if(MainApplication.isUserSignedIn())
		{
			if(!device.isAssociated())
			{
				device.startDeviceAssociation(userManager.getUserToken());
			}
		}
		*/
	}
	
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onDeviceConnected(DeviceConnectedEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onDeviceConnected e:" + event);

		WaltzDevice device = event.getDevice();
		
		onInternalDeviceConnected(device);
	}
	
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onDeviceConnectionFail(DeviceConnectionFailEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onDeviceConnectionFail e:" + event);

		if(mProgressDialog!=null && mProgressDialog.isShowing())
			mProgressDialog.dismiss();

		WaltzDevice device = event.getDevice();
		int error = event.getError();

		if(device==null)
			return;

		if(mCurrentDeviceIndex<0 || mCurrentDeviceIndex>=MainApplication.getWaltzDeviceNumber())
			return;
			
		WaltzDevice currentDevice = MainApplication.getWaltzDevice(mCurrentDeviceIndex);

		if(currentDevice==null)
			return;
			
		if(!device.equals(currentDevice))
			return;

		showConnectionFailDialog(device, error);

		mCurrentDeviceIndex = -1;
	}

	// Access code issue. Handle tutk disconnected error.
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onDeviceUninitialized(DeviceUninitializedEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onDeviceConnectionFail e:" + event);

		WaltzDevice device = event.getDevice();

		if(device==null)
			return;

		if(mCurrentDeviceIndex<0 || mCurrentDeviceIndex>=MainApplication.getWaltzDeviceNumber())
			return;
			
		WaltzDevice currentDevice = MainApplication.getWaltzDevice(mCurrentDeviceIndex);

		if(currentDevice==null)
			return;

		if(!device.equals(currentDevice))
			return;

		mCurrentDeviceIndex = -1;
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onDeviceAccessCodeDone(AccessCodeDoneEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onDeviceAccessCodeDone e:" + event);

		WaltzDevice device = event.getDevice();

		if(device==null)
			return;
		
		if(mCurrentDeviceIndex<0 || mCurrentDeviceIndex>=MainApplication.getWaltzDeviceNumber())
			return;
			
		WaltzDevice currentDevice = MainApplication.getWaltzDevice(mCurrentDeviceIndex);

		if(currentDevice==null)
			return;
			
		if(!device.equals(currentDevice))
			return;
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onDeviceAccessCodeFail(AccessCodeFailEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onDeviceAccessCodeFail e:" + event);

		if(mProgressDialog!=null && mProgressDialog.isShowing())
			mProgressDialog.dismiss();
		
		WaltzDevice device = event.getDevice();

		if(DEBUG)
			Log.d(TAG, "onDeviceAccessCodeFail d:" + device);

		if(device==null)
			return;
		
		if(DEBUG)
			Log.d(TAG, "onDeviceAccessCodeFail i:" + mCurrentDeviceIndex);

		if(mCurrentDeviceIndex<0 || mCurrentDeviceIndex>=MainApplication.getWaltzDeviceNumber())
			return;
			
		WaltzDevice currentDevice = MainApplication.getWaltzDevice(mCurrentDeviceIndex);

		if(DEBUG)
			Log.d(TAG, "onDeviceAccessCodeFail c:" + currentDevice);

		if(currentDevice==null)
			return;
			
		if(!device.equals(currentDevice))
			return;

		showInputAccessCodeDialog(mCurrentDeviceIndex);
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onRefreshHTUIInfo(RefreshHTUIInfoEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onRefreshHTUIInfo e:" + event);

		mDeviceMediaFragment.refreshHTUIInfo();
		mDeviceListFragment.updateAllItem();
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onRefreshMotionWindowView(RefreshMotionWindowViewEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onRefreshMotionWindowView e:" + event);

		//20160311 joyce
		if((event.getDevice().getFirmwareVersion().compareToIgnoreCase(event.getDevice().mVersion_115) < 0)&&
		   (event.getDevice().getFirmwareVersion().length() <= event.getDevice().mVersion_115.length()))
		{
			mDeviceMotionFragment.refreshMotionWindowView(event.getWindowId());
		}
		else{
			if(event.getWindowId() == -1)
				mDeviceMotionFragment.refreshAllMotionWindowView();
			else
				mDeviceMotionFragment.refreshMotionWindowView(event.getWindowId());
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onRefreshInfoPageView(RefreshInfoPageEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onRefreshInfoPageView e:" + event);

		mDeviceInfoFragment.refreshInfoPageView(event.isSirenMode(), event.isBackupMode());
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onRefreshMediaPageView(RefreshMediaPageEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onRefreshMediaPageView e:" + event);

		mDeviceMediaFragment.refreshMediaPageView(event.guardianMode(), event.isPanicMode(), event.isAudioMute());
	}
	
	@Override
	public void onDeviceInfo(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "onDeviceInfo");

		if(device==null)
			return;
			
		hideAllFragments();
		
		if(mIsStopped || mIsInstanceStateSaved)
			return;
		
		mDeviceInfoFragment.setDevice(device);
		mDeviceInfoFragment.updateInfo();

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		fragmentTransaction.show(mDeviceInfoFragment);
		fragmentTransaction.hide(mDevicePagerFragment);

		fragmentTransaction.addToBackStack(null);

		fragmentTransaction.commit();
	}
	
	@Override
	public void onShowDevice(int index)
	{
		if(DEBUG)
			Log.d(TAG, "onShowDevice i:" + index);
			
		if(!MainApplication.isTutkInitialized())
		{
			showInitializationFailDialog(null, null, TutkErrorCode.TUTK_ERROR_IOTC_NOT_INITIALIZED);
			return;
		}

		WaltzDevice device = MainApplication.getWaltzDevice(index);

		if(device==null)
			return;
			
        device.setIsShowDevice(true);
		mCurrentDeviceIndex = index;
		
		String uid = device.getUid();
		
		if(MainApplication.isWaltzDeviceTutkConnected(uid))
		{
			if(device.isAccessCodeCheckedDone() && !device.isAccessCodeCheckedSuccess())
			{
				if(mProgressDialog!=null && mProgressDialog.isShowing())
					mProgressDialog.dismiss();

				if(DEBUG)
					Log.d(TAG, "onShowDevice show access code dialog");

				showInputAccessCodeDialog(mCurrentDeviceIndex);
			}
			else
			{
				if(mProgressDialog!=null)
				{
					mProgressDialog.setMessage(getString(R.string.main_connecting) + " " + device.getName() + "...");
					mProgressDialog.show();
				}

				onInternalDeviceConnected(device);
			}
		}
		else
		{
			if(mProgressDialog!=null)
			{
				mProgressDialog.setMessage(getString(R.string.main_connecting) + " " + device.getName() + "...");
				mProgressDialog.show();
			}
					
			MainApplication.connectWaltzDevice(uid);
		}
	}
	
	private Runnable mTutkConnectedRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if(DEBUG)
				Log.d(TAG, "mTutkConnectedRunnable run");

			if(mProgressDialog!=null)
			{
				mProgressDialog.setMessage(getString(R.string.main_connecting) + " " + mTutkDeviceName + "...");
				mProgressDialog.show();
			}
		}
	};
	
	private Runnable mTutkConnectionFailRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if(DEBUG)
				Log.d(TAG, "mTutkConnectionFailRunnable run");

			if(mProgressDialog!=null && mProgressDialog.isShowing())
				mProgressDialog.dismiss();

			if(mTutkError== TutkErrorCode.TUTK_ERROR_NONE)
			{
				showDeviceList();
			}
			else
			{
				showInitializationFailDialog(mTutkDeviceName, mTutkDeviceUid, mTutkError);
			}
		}
	};

	private Runnable mConnectionExceedRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if(DEBUG)
				Log.d(TAG, "mConnectionExceedRunnable run");

			MainApplication.connectWaltzDeviceAndDisconnectAllOther(mTutkDeviceUid);
		}
	};

	@Subscribe
	public void onTutkConnectionChanged(TutkConnectionChangedEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onTutkConnectionChanged e:" + event);
	
		String uid = event.getUid();
		boolean connected = event.isConnected();
		int error = event.getError();

		if(DEBUG)
			Log.d(TAG, "onTutkConnectionChanged u:" + uid + " c:" + connected + " e:" + error);

		WaltzDevice device = MainApplication.getWaltzDevice(mCurrentDeviceIndex);

		if(device==null)
			return;

		if(!uid.equalsIgnoreCase(device.getUid()))
			return;

		mTutkDeviceName = device.getName();
		mTutkDeviceUid = device.getUid();
		
		if(connected)
		{
			mTutkError = TutkErrorCode.TUTK_ERROR_NONE;
			
			mTutkConnectedHandler.removeCallbacks(mTutkConnectedRunnable);
			mTutkConnectedHandler.postDelayed(mTutkConnectedRunnable, 0);
		}
		else
		{
			switch(error)
			{
				case TutkErrorCode.TUTK_ERROR_TUNNEL_MAX_CONNECTION_EXCEED:
				case TutkErrorCode.TUTK_ERROR_TUNNEL_MAX_SESSION_EXCEED:
				{
					mConnectionExceedHandler.removeCallbacks(mConnectionExceedRunnable);
					mConnectionExceedHandler.postDelayed(mConnectionExceedRunnable, 0);

					break;
				}
				
				default:
				{
					mTutkError = error;
			
					mTutkConnectionFailHandler.removeCallbacks(mTutkConnectionFailRunnable);
					mTutkConnectionFailHandler.postDelayed(mTutkConnectionFailRunnable, 0);
					
					break;
				}
			}
		}
	}

	@Override
	public void onConnectWifi(int index)
	{
		if(DEBUG)
			Log.d(TAG, "onConnectWifi i:" + index);
		
		if(index<0 || index>=MainApplication.getWaltzDeviceNumber())
			return;
			
		try
		{
			Intent intent = new Intent(this, NetworkActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			
			intent.putExtra(NetworkActivity.WALTZ_INDEX, index);

			startActivityForResult(intent, RESULT_CONNECT_WIFI);
		}
		catch(ActivityNotFoundException e)
		{
		}
	}

	@Override
	public void onConnectWifi(WaltzDevice device)
	{
		int index;

		if(device == null)
			return;

		index = MainApplication.getWaltzDeviceIndex(device);

		if(DEBUG)
			Log.d(TAG, "onConnectWifi i:" + index);

		try
		{
			Intent intent = new Intent(this, NetworkActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

			intent.putExtra(NetworkActivity.WALTZ_INDEX, index);

			//startActivityForResult(intent, RESULT_CONNECT_WIFI);
			startActivity(intent);
		}
		catch(ActivityNotFoundException e)
		{
		}
	}

	@Override
	public void onSwitchWifiClientMode(int index)
	{
		if(DEBUG)
			Log.d(TAG, "onSwitchWifiClientMode i:" + index);
		
		WaltzDevice device = MainApplication.getWaltzDevice(index);
		
		if(device==null)
			return;
		
		device.switchToWifiClientMode();
	}

	@Override
	public void onSwitchWifiApMode(int index)
	{
		if(DEBUG)
			Log.d(TAG, "onSwitchWifiApMode i:" + index);
		
		WaltzDevice device = MainApplication.getWaltzDevice(index);
		
		if(device==null)
			return;
		
		device.switchToWifiApMode();
	}

	@Override
	public void onDeleteDevice(int index)
	{
		if(DEBUG)
			Log.d(TAG, "onDeleteDevice i:" + index);
		
		showDeleteDeviceDialog(index);
	}

	@Override
	public void onOwnerDeleteDevice(int index, int count)
	{
		if(DEBUG)
			Log.d(TAG, "onOwnerDeleteDevice i:" + index + ", count: " + count);

		showOwnerDeleteDeviceDialog(index, count);
	}

	@Override
	public void onRenameDevice(int index)
	{
		if(DEBUG)
			Log.d(TAG, "onRenameDevice i:" + index);
		
		showRenameDeviceDialog(index);
	}

	@Override
	public void onOptionBackup(WaltzDevice device, boolean checked)
	{
		if(DEBUG)
			Log.d(TAG, "onOptionBackup d:" + device + " c:" + checked);
		
		if(device==null)
			return;
		
		device.setDropboxMode(checked);
		
		if(!MainApplication.hasDropboxAccessToken())
			return;
		
		if(checked)
			device.setDropboxAccessToken(MainApplication.getDropboxAccessToken());
	}

	@Override
	public void onOptionSiren(WaltzDevice device, boolean checked)
	{
		if(DEBUG)
			Log.d(TAG, "onOptionSiren d:" + device + " c:" + checked);
		
		if(device==null)
			return;

		String status;
		if(checked)
			status = "1";//siren sensor up
		else
			status = "2";//siren sensor down
		device.setSensorSirenMode(checked, "Siren", status);
	}

	@Override
	public void onUpgradeDevice(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "onUpgradeDevice d:" + device);
		
		if(device==null)
			return;
		
		device.upgradeFirmware();
	}

	@Override
	public void onRestoreDevice(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "onRestoreDevice d:" + device);
		
		showRestoreDeviceDialog(device);
	}

	@Override
	public void onCameraFullscreen(boolean fullscreen)
	{
		if(DEBUG)
			Log.d(TAG, "onCameraFullscreen");

		if(mIsStopped || mIsInstanceStateSaved)
			return;

		if(mDevicePagerFragment==null)
			return;
		
		if(fullscreen)
		{
			hideToolbar();

			mDevicePagerFragment.setMediaFullscreen(true);

			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		else
		{
			showToolbar();

			mDevicePagerFragment.setMediaFullscreen(false);

			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}

	@Override
	public void onVideoListFullscreen(boolean fullscreen)
	{
		if(DEBUG)
			Log.d(TAG, "onVideoListFullscreen");

		if(mIsStopped || mIsInstanceStateSaved)
			return;

        if(mDevicePagerFragment==null)
            return;

		if(fullscreen)
		{
			hideToolbar();

			mDevicePagerFragment.setVideoListFullscreen(true);

			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		else
		{
			showToolbar();

			mDevicePagerFragment.setVideoListFullscreen(false);
			mDeviceVideoListFragment.stopVideoPlay();
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}

	@Override
	public void onMotionFullscreen(boolean fullscreen)
	{
		if(DEBUG)
			Log.d(TAG, "onMotionFullscreen");

		if(mIsStopped || mIsInstanceStateSaved)
			return;

		if(mDevicePagerFragment==null)
			return;
		
		if(fullscreen)
		{
			hideToolbar();

			mDevicePagerFragment.setMotionFullscreen(true);

			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		else
		{
			showToolbar();

			mDevicePagerFragment.setMotionFullscreen(false);

			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}

	@Override
	public void onShowNewAccessCodeDialog(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "onShowNewAccessCodeDialog d:" + device);
		
		if(device==null)
			return;
		
		showNewAccessCodeDialog(device);
	}
	
	private static class AudioRunnable implements Runnable
	{
		private WaltzDevice mDevice;
		
		public AudioRunnable(WaltzDevice device)
		{
			mDevice = device;
		}
		
		@Override
		public void run()
		{
			if(DEBUG)
				Log.d(TAG, "AudioRunnable run start");
			
			try
			{	
				mAudioRecord.startRecording();
			}
			catch(IllegalStateException e)
			{
				mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mAudioRecordBufferSize);
				mAudioRecord.startRecording();
			}
			
			mDevice.startTalking();

			while(mIsAudioRecording)
			{
				short[] bufferRecord = new short[mAudioRecordBufferSize];

				int byteRead = mAudioRecord.read(bufferRecord, 0, mAudioRecordBufferSize);
					
				if(byteRead<=0)
					continue;
					
				byte[] bufferOut = new byte[byteRead];

				mAudioCodec.encode(bufferRecord, byteRead, bufferOut, 0);

				mDevice.sendTalkingAudio(bufferOut);
			}
			
			mDevice.stopTalking();
			mAudioRecord.stop();

			if(DEBUG)
				Log.d(TAG, "AudioRunnable run end");
		}
	}
	
	@Override
	public void onTakeSnapshot(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "onTakeSnapeshot d:" + device);

		if(device==null)
			return;

		if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
		{
			if(ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
			{
				if(DEBUG)
					Log.d(TAG, "onTakeSnapeshot shouldShowRequestPermissionRationale true");
			}
			else
			{
				if(DEBUG)
					Log.d(TAG, "onTakeSnapeshot shouldShowRequestPermissionRationale false");
			}
			
			mPermissionDevice = device;

			ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
		}
		else
		{
			MainApplication.playShutterSound();
		
			device.takeAndSaveCameraSnapshot(device.getDeviceId());
		}
	}
	
	@Override
	public void onMute(WaltzDevice device, boolean mute)
	{
		if(DEBUG)
			Log.d(TAG, "onMute d:" + device + " m:" + mute);

		if(device==null)
			return;

		device.muteCurrentStream(mute);
	}
	
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onStreamMuteChanged(StreamMuteChangedEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onStreamMuteChanged e:" + event);

		mStreamMuteChangedHandler.removeCallbacks(mStreamMuteChangedRunnable);
		mStreamMuteChangedHandler.postDelayed(mStreamMuteChangedRunnable, STREAM_CHANGED_DELAY);
	}

	private Runnable mStreamMuteChangedRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if(DEBUG)
				Log.d(TAG, "mStreamMuteChangedRunnable run");

			if(mDeviceMediaFragment==null)
				return;
				
			mDeviceMediaFragment.updateStatus();
			mDeviceMediaFragment.refreshStreamWithoutProgress();
		}
	};
	
	@Override
	public void onTalk(WaltzDevice device, boolean record)
	{
		if(DEBUG)
			Log.d(TAG, "onTalk d:" + device + " r:" + record);

		if(device==null)
			return;
			
		if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
		{
			if(ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO))
			{
				if(DEBUG)
					Log.d(TAG, "onTalk shouldShowRequestPermissionRationale true");
			}
			else
			{
				if(DEBUG)
					Log.d(TAG, "onTalk shouldShowRequestPermissionRationale false");
			}
			
			mPermissionDevice = device;

			ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
		}
		else
		{
			if(record)
			{
				if(mAudioThread!=null)
					mIsAudioRecording = false;
				
				mAudioThread = new Thread(new AudioRunnable(device));
			
				mIsAudioRecording = true;
			
				mAudioThread.start();
			}
			else
			{
				if(mAudioThread!=null)
					mIsAudioRecording = false;
			}
		}
	}

	private void hideAllFragments()
	{
		if(mIsStopped || mIsInstanceStateSaved)
			return;

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		fragmentTransaction.hide(mDevicePagerFragment);
		fragmentTransaction.hide(mHelpFragment);
		fragmentTransaction.hide(mAboutFragment);
		fragmentTransaction.hide(mDropboxFragment);
		fragmentTransaction.hide(mDeviceListFragment);
		fragmentTransaction.hide(mDeviceInfoFragment);

		fragmentTransaction.commit();
	}

	private void showDropboxAssociation()
	{
		if(DEBUG)
			Log.d(TAG, "showDropboxAssociation");

		MainApplication.updateDropboxCurrentUser();

		hideAllFragments();
		
		if(mIsStopped || mIsInstanceStateSaved)
			return;
			
		mShowDropbox = true;

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		fragmentTransaction.show(mDropboxFragment);

		fragmentTransaction.commit();

		mToolbar.setTitle(R.string.title_dropbox);
	}
	
	private void showDeviceList()
	{
		if(DEBUG)
			Log.d(TAG, "showDeviceList");

		hideAllFragments();
		
		if(mIsStopped || mIsInstanceStateSaved)
			return;
			
		mShowDropbox = false;
		mCurrentDeviceIndex = -1;

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		fragmentTransaction.show(mDeviceListFragment);

		fragmentTransaction.commit();

		mToolbar.setTitle(R.string.title_device);
	}
	
	private void showHelp()
	{
		if(DEBUG)
			Log.d(TAG, "showHelp");

		hideAllFragments();

		if(mIsStopped || mIsInstanceStateSaved)
			return;
			
		mShowDropbox = false;

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		fragmentTransaction.show(mHelpFragment);

		fragmentTransaction.commit();

		mToolbar.setTitle(R.string.title_help);
	}
			
	private void showAbout()
	{
		if(DEBUG)
			Log.d(TAG, "showAbout");

		hideAllFragments();

		if(mIsStopped || mIsInstanceStateSaved)
			return;
			
		mShowDropbox = false;

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		fragmentTransaction.show(mAboutFragment);

		fragmentTransaction.commit();

		mToolbar.setTitle(R.string.title_about);
	}

	private void setupLayout()
	{
		if(DEBUG)
			Log.d(TAG, "setupLayout");

		mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer);
		mNavigationView = (NavigationView) findViewById(R.id.navigation_view);
		
		mToolbar.setTitleTextColor(getResources().getColor(R.color.waltz_title));
		
		mNavigationView.setNavigationItemSelectedListener(this);
		
		setSupportActionBar(mToolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		mToolbar.setNavigationIcon(R.drawable.ic_drawer);
		
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.open, R.string.close);
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		mDrawerToggle.syncState();
		
		mProgressDialog = new ProgressDialog(this, R.style.WaltzProgessBar);

		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		mProgressDialog.setCanceledOnTouchOutside(false);

		mDevicePagerFragment = new DevicePagerFragment();
		mDeviceMediaFragment= new DeviceMediaFragment();
		mDeviceMotionFragment = new DeviceMotionFragment();
		mDropboxFragment = new DropboxFragment();
		mDeviceListFragment= new DeviceListFragment();
		mDeviceInfoFragment = new DeviceInfoFragment();
		mHelpFragment = new HelpFragment();
		mAboutFragment = new AboutFragment();
		mUserListFragment = new UserListFragment(); // Add Device's member list
		mSensorGridFragment = new SensorGridFragment();
		mDeviceVideoListFragment = new DeviceVideoListFragment();
		mEventHistoryFragment = new EventHistoryFragment();

		mDeviceMediaFragment.setOnDeviceInfoListener(this);
		mDeviceMotionFragment.setOnDeviceInfoListener(this);
		mSensorGridFragment.setOnDeviceInfoListener(this);
		mDeviceVideoListFragment.setOnDeviceInfoListener(this);
		mUserListFragment.setOnDeviceInfoListener(this);
		mEventHistoryFragment.setOnDeviceInfoListener(this);
		
//		mDevicePagerFragment.setFragmentManager(mFragmentManager);
		mDevicePagerFragment.setOnFullscreenListener(this);
		
		mDropboxFragment.setOnLoginListener(this);
		
		mUserListFragment.setToolbar(mToolbar); // Add Device's member list
		mUserListFragment.setOnOwnerChangeListener(this); // Add Device's member list

		mSensorGridFragment.setToolbar(mToolbar);
		
		mDeviceVideoListFragment.setToolbar(mToolbar);
		mDeviceVideoListFragment.setOnFullscreenListener(mDevicePagerFragment);

		mDeviceMediaFragment.setToolbar(mToolbar);
		mDeviceMediaFragment.setOnMediaListener(this);
		mDeviceMediaFragment.setOnFullscreenListener(mDevicePagerFragment);
		
		mDeviceMotionFragment.setToolbar(mToolbar);
		mDeviceMotionFragment.setOnFullscreenListener(mDevicePagerFragment);

		mDeviceInfoFragment.setToolbar(mToolbar);
		mDeviceInfoFragment.setOnInfoListener(this);
		
		mEventHistoryFragment.setToolbar(mToolbar);

		mDeviceListFragment.setToolbar(mToolbar);
		mDeviceListFragment.setOnSelectionListener(this);

		FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

		//fragmentTransaction.add(R.id.container, mDeviceMediaFragment);
		fragmentTransaction.add(R.id.container, mDevicePagerFragment);
		fragmentTransaction.add(R.id.container, mDropboxFragment);
		fragmentTransaction.add(R.id.container, mDeviceListFragment);
		fragmentTransaction.add(R.id.container, mDeviceInfoFragment);
		fragmentTransaction.add(R.id.container, mHelpFragment);
		fragmentTransaction.add(R.id.container, mAboutFragment);
		//fragmentTransaction.add(R.id.container, mUserListFragment); // Add Device's member list
		//fragmentTransaction.add(R.id.container, mSensorGridFragment);

		//fragmentTransaction.hide(mDeviceMediaFragment);
		fragmentTransaction.hide(mDevicePagerFragment);
		fragmentTransaction.hide(mDropboxFragment);
		fragmentTransaction.hide(mDeviceListFragment);
		fragmentTransaction.hide(mDeviceInfoFragment);
		fragmentTransaction.hide(mHelpFragment);
		fragmentTransaction.hide(mAboutFragment);
		//fragmentTransaction.hide(mUserListFragment); // Add Device's member list
		//fragmentTransaction.hide(mSensorGridFragment);

		fragmentTransaction.commit();
	}
	
	private void resetLayout()
	{
		if(DEBUG)
			Log.d(TAG, "resetLayout");
		
		MenuItem menuDropbox = mNavigationView.getMenu().findItem(R.id.navigation_dropbox);
		MenuItem menuDeviceList = mNavigationView.getMenu().findItem(R.id.navigation_device_list);
		MenuItem menuSignin = mNavigationView.getMenu().findItem(R.id.navigation_signin);
		MenuItem menuSignout = mNavigationView.getMenu().findItem(R.id.navigation_signout);

		View headerLayout = mNavigationView.getHeaderView(0);
		TextView userInfoView = (TextView) headerLayout.findViewById(R.id.navigation_header_user_name);
			
		if(MainApplication.isUserSignedIn())
		{
			if(menuSignin!=null)
			{
				menuSignin.setEnabled(false);
				menuSignin.setVisible(false);
			}

			if(menuSignout!=null)
			{
				menuSignout.setEnabled(true);
				menuSignout.setVisible(true);
			}
			
			if(menuDropbox!=null)
			{
				menuDropbox.setEnabled(true);
				menuDropbox.setVisible(true);
			}
			
			UserManager userManager = MainApplication.getUserManager();

			String name = userManager.getUsername();
			
			userInfoView.setText(name);

			userInfoView.setVisibility(View.VISIBLE);
		}
		else
		{
			if(menuSignin!=null)
			{
				menuSignin.setEnabled(true);
				menuSignin.setVisible(true);
			}

			if(menuSignout!=null)
			{
				menuSignout.setEnabled(false);
				menuSignout.setVisible(false);
			}
			
			if(menuDropbox!=null)
			{
				menuDropbox.setEnabled(false);
				menuDropbox.setVisible(false);
			}

			userInfoView.setVisibility(View.GONE);
		}
		
		if(mShowDropbox)
		{
			showDropboxAssociation();

			if(menuDropbox!=null)
				menuDropbox.setChecked(true);
		}
		else
		{
			showDeviceList();

			mDeviceListFragment.notifyDataSetChanged();
		
			if(menuDeviceList!=null)
				menuDeviceList.setChecked(true);
		}
	}

	private void resetLayoutOrientation()
	{
	}
	
	//
	//
	// Dialog
	//
	//
	
	private void showDeleteDeviceDialog(final int index)
	{
		if(DEBUG)
			Log.d(TAG, "showDeleteDeviceDialog i:" + index);
			
		final WaltzDevice device = MainApplication.getWaltzDevice(index);

		if(MainApplication.isUserSignedIn() && device.isOwnerScope())
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

			builder.setIcon(R.drawable.ic_launcher);
			builder.setTitle(R.string.app_name);
			builder.setMessage(getString(R.string.device_owner)+" ("+device.getName()+").");
			builder.setCancelable(false);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int id)
				{
//					ParsePush.unsubscribeInBackground("WALTZ_" + device.getSerial());
//					unsubscribeGcmChannel("WALTZ_" + device.getSerial());
				}
			});

			builder.create().show();
		}
		else
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

			builder.setIcon(R.drawable.ic_launcher);
			builder.setTitle(R.string.app_name);
			builder.setMessage(getString(R.string.sure_to_remove)+" ("+device.getName()+") ?");
			builder.setCancelable(false);
			builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int id)
				{
					MainApplication.removeWaltzDevice(index);

					mDeviceListFragment.notifyDataSetChanged();

					onRefreshDevice();

					ParsePush.unsubscribeInBackground("WALTZ_" + device.getSerial());
					unsubscribeGcmChannel("WALTZ_" + device.getSerial());
				}
			});

			builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int id)
				{
				}
			});

			builder.create().show();
		}
	}

	private void showOwnerDeleteDeviceDialog(final int index, final int count)
	{
		if(DEBUG)
			Log.d(TAG, "showOwnerDeleteDeviceDialog i:" + index);

		final WaltzDevice device = MainApplication.getWaltzDevice(index);

		if(MainApplication.isUserSignedIn() && device.isOwnerScope() && (count>1))
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

			builder.setIcon(R.drawable.ic_launcher);
			builder.setTitle(R.string.app_name);
			builder.setMessage(getString(R.string.device_owner) + " (" + device.getName() + "). " + getString(R.string.remind_owner));
			builder.setCancelable(false);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int id)
				{
//					ParsePush.unsubscribeInBackground("WALTZ_" + device.getSerial());
//					unsubscribeGcmChannel("WALTZ_" + device.getSerial());
				}
			});

			builder.create().show();
		}
		else
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

			builder.setIcon(R.drawable.ic_launcher);
			builder.setTitle(R.string.app_name);
			builder.setMessage(getString(R.string.sure_to_remove)+" ("+device.getName()+") ? " + getString(R.string.device_owner) + ".");
			builder.setCancelable(false);
			builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int id)
				{
					MainApplication.removeWaltzDevice(index);

					mDeviceListFragment.notifyDataSetChanged();

					onRefreshDevice();

					ParsePush.unsubscribeInBackground("WALTZ_" + device.getSerial());
					unsubscribeGcmChannel("WALTZ_" + device.getSerial());
				}
			});

			builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int id)
				{
				}
			});

			builder.create().show();
		}
	}

	private void showRestoreDeviceDialog(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "showRestoreDeviceDialog d:" + device);
			
		if(device==null)
			return;

		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.app_name);
		builder.setMessage(getString(R.string.sure_to_restore) + " (" + device.getName() + ") ?");
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.restore_default, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int id) {
				device.restoreSystemDefault();
			}
		});

		builder.setNeutralButton(R.string.restore_network, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int id) {
				device.restoreSystemKeepNetwork();
			}
		});

		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
			}
		});

		builder.create().show();
	}
	
	private void showRenameDeviceDialog(int index)
	{
		if(DEBUG)
			Log.d(TAG, "showRenameDeviceDialog i:" + index);
			
		final WaltzDevice device = MainApplication.getWaltzDevice(index);

		LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
		View dialogView = inflater.inflate(R.layout.dialog_edit_device, null);

		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		final EditText deviceNameText = (EditText) dialogView.findViewById(R.id.text_device_name);
		
		String name = device.getName();
				
		deviceNameText.setText(name);
		deviceNameText.setSelection((name.length() > 0) ? (name.length()) : 0);

		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.dialog_edit_device);
		builder.setView(dialogView);
		builder.setCancelable(false);
		
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
				String deviceName = deviceNameText.getText().toString();

				if (deviceName != null && deviceName.length() > 0)
				{
					device.setName(deviceName);

					MainApplication.renameWaltzDevice(index, deviceName); // rename device name in lcoud
					MainApplication.saveDeviceList();

					mDeviceListFragment.notifyDataSetChanged();
				}
			}
		});

		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int id) {
			}
		});

		builder.create().show();
	}

	private void showInputAccessCodeDialog(int index)
	{
		if(DEBUG)
			Log.d(TAG, "showInputAccessCodeDialog i:" + index);

		if(index < 0 || index >= MainApplication.getWaltzDeviceNumber()) return;

		if(mInputAccessCodeDialog!=null)
		{
			mInputAccessCodeDialog.dismiss();
		}

		final WaltzDevice device = MainApplication.getWaltzDevice(index);

		LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
		View dialogView = inflater.inflate(R.layout.dialog_access_code_input, null);

		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		final TextView textView = (TextView) dialogView.findViewById(R.id.input_access_code_text);
		final PinEditText pinView = (PinEditText) dialogView.findViewById(R.id.input_access_code_view);
		
		final String name = device.getName();
		
		textView.setText(getString(R.string.dialog_access_code_input) + " " + name);
				
		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.dialog_access_code);
		builder.setView(dialogView);
		builder.setCancelable(false);
		
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int id) {
				String pinText = pinView.getText().toString();

				//20160223 joyce add
				if (pinText == null || pinText.isEmpty() || pinText.length() <= 5) {
					showAccessCodeLengthWrongDialog();
					return;
				}

				device.checkAccessCode(pinText);

				if (mProgressDialog != null) {
					mProgressDialog.setMessage(getString(R.string.main_connecting) + " " + name + "...");
					mProgressDialog.show();
				}
			}
		});

		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
			}
		});
		
		mInputAccessCodeDialog = builder.create();

		pinView.setOnPinListener(new PinEditText.OnPinListener()
		{
			@Override
			public void onPincodeDone()
			{
				if (DEBUG)
					Log.d(TAG, "showInputAccessCodeDialog t:" + pinView.getText().toString());
			}
		});

		mInputAccessCodeDialog.show();
	}

	private void showNewAccessCodeDialog(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "showNewAccessCodeDialog d:" + device);
		
		if(mNewAccessCodeDialog!=null)
		{
			mNewAccessCodeDialog.dismiss();
		}
		
		LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
		View dialogView = inflater.inflate(R.layout.dialog_access_code_new, null);

		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		final TextView firstTextView = (TextView) dialogView.findViewById(R.id.new_first_access_code_text);
        final TextView secondTextView = (TextView) dialogView.findViewById(R.id.new_second_access_code_text);
		final PinEditText firstView = (PinEditText) dialogView.findViewById(R.id.new_first_access_code_view);
		final PinEditText secondView = (PinEditText) dialogView.findViewById(R.id.new_second_access_code_view);
		
		firstView.requestFocus();
		
		String name = device.getName();
		
		firstTextView.setText(getString(R.string.dialog_access_code_new) + " " + name);
        secondTextView.setText(getString(R.string.dialog_access_code_again));
				
		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.dialog_access_code);
		builder.setView(dialogView);
		builder.setCancelable(false);
		
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int id) {
				String firstText = firstView.getText().toString();
				String secondText = secondView.getText().toString();

				if (firstText == null || secondText == null || !firstText.equals(secondText)) {
					showAccessCodeNotMatchDialog();
					return;
				}

				device.changeAccessCode(firstText);
			}
		});

		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int id) {
			}
		});
		
		mNewAccessCodeDialog = builder.create();

		firstView.setOnPinListener(new PinEditText.OnPinListener() {
			@Override
			public void onPincodeDone() {
				if (DEBUG)
					Log.d(TAG, "showNewAccessCodeDialog ft:" + firstView.getText().toString());

				secondView.requestFocus();
			}
		});

		secondView.setOnPinListener(new PinEditText.OnPinListener() {
			@Override
			public void onPincodeDone() {
				if (DEBUG)
					Log.d(TAG, "showNewAccessCodeDialog st:" + secondView.getText().toString());
			}
		});

		mNewAccessCodeDialog.show();
	}

	private void showSignOutDialog()
	{
		if(DEBUG)
			Log.d(TAG, "showSignOutDialog");
			
		UserManager userManager = MainApplication.getUserManager();
			
		if(!MainApplication.isUserSignedIn())
			return;
			
		if(userManager==null)
			return;

		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.app_name);
		builder.setMessage(getString(R.string.sure_to_sign_out) + " (" + userManager.getUsername() + ") ?");
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
			
				for(int i=0; i<MainApplication.getWaltzDeviceNumber(); i++)
				{
					WaltzDevice device = MainApplication.getWaltzDevice(i);
					
					ParsePush.unsubscribeInBackground("WALTZ_" + device.getSerial());
					unsubscribeGcmChannel("WALTZ_" + device.getSerial());
				}
				
				MainApplication.removeAllWaltzDevices();
				
				signOut();
				MainApplication.saveDeviceList();
			}
		});

		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int id) {
			}
		});

		builder.create().show();
	}

	private void showQuitDialog()
	{
		if(DEBUG)
			Log.d(TAG, "showQuitDialog");
			
		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.app_name);
		builder.setMessage(R.string.sure_to_quit);
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
				finish();
			}
		});

		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
			}
		});

		builder.create().show();
	}

	private void showInitializationFailDialog(String name, String uid, int error)
	{
		if(DEBUG)
			Log.d(TAG, "showInitializationFailDialog n:" + name + " u:" + uid + " e:" + error);
			
		String message = getString(R.string.dialog_fail_to_connect) + " " + name;
		
		switch(error)
		{
			case TutkErrorCode.TUTK_ERROR_IOTC_NOT_INITIALIZED:
			{
				message = getString(R.string.tutk_error_not_initialized);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_IOTC_DEVICE_NOT_FOUND:
			{
				message = String.format(getString(R.string.tutk_error_device_not_found), name);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_IOTC_TIMEOUT:
			case TutkErrorCode.TUTK_ERROR_IOTC_TIMEOUT_DISCONNECT:
			{
				message = String.format(getString(R.string.tutk_error_connection_timeout), name);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_IOTC_RELAY_FAIL:
			{
				message = String.format(getString(R.string.tutk_error_relay_fail), name);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_TUNNEL_UID_NO_PERMISSION:
			case TutkErrorCode.TUTK_ERROR_TUNNEL_UID_RELAY_NOT_SUPPORT:
			case TutkErrorCode.TUTK_ERROR_IOTC_NO_PERMISSION:
			case TutkErrorCode.TUTK_ERROR_IOTC_RELAY_NOT_SUPPORT:
			{
				message = String.format(getString(R.string.tutk_error_relay_not_support), name);
				break;
			}

			case TutkErrorCode.TUTK_ERROR_TUNNEL_AUTH_FAIL:
			{
				message = String.format(getString(R.string.tutk_error_auth_fail), name);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_TUNNEL_NETWORK_UNREACHABLE:
			case TutkErrorCode.TUTK_ERROR_IOTC_NETWORK_UNREACHABLE:
			{
				message = getString(R.string.tutk_error_network_unreachable);
				break;
			}

			case TutkErrorCode.TUTK_ERROR_TUNNEL_MAX_CONNECTION_EXCEED:
			case TutkErrorCode.TUTK_ERROR_TUNNEL_MAX_SESSION_EXCEED:
			case TutkErrorCode.TUTK_ERROR_IOTC_MAX_SESSION_EXCEED:
			case TutkErrorCode.TUTK_ERROR_IOTC_MAX_CHANNEL_EXCEED:
			case TutkErrorCode.TUTK_ERROR_IOTC_DEVICE_MAX_SESSION_EXCEED:
			{
				message = String.format(getString(R.string.tutk_error_max_connection_exceed), name);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_TUNNEL_UID_NOT_LICENSED:
			case TutkErrorCode.TUTK_ERROR_IOTC_UID_NOT_LICENSED:
			{
				message = String.format(getString(R.string.tutk_error_uid_not_licensed), uid, name);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_IOTC_INVALID_SID:
			case TutkErrorCode.TUTK_ERROR_TUNNEL_INVALID_SID:
			{
				message = String.format(getString(R.string.tutk_error_sid_invalid), name);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_IOTC_SERVER_NO_RESPONSE:
			case TutkErrorCode.TUTK_ERROR_IOTC_SERVER_HOSTNAME_FAIL:
			{
				message = getString(R.string.tutk_error_server_unreachable);
				break;
			}
			
			case TutkErrorCode.TUTK_ERROR_TUNNEL_DISCONNECTED:
			{
				message = String.format(getString(R.string.tutk_error_disconnected), name);
				break;
			}
			
			default:
				message = getString(R.string.dialog_fail_to_connect) + " " + name;
				break;
		}

		if(mInitializationFailDialog!=null)
		{
			mInitializationFailDialog.setMessage(message);
			mInitializationFailDialog.show();
		}
		else
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

			builder.setIcon(R.drawable.ic_launcher);
			builder.setTitle(R.string.dialog_connection_fail);
			builder.setCancelable(false);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int id)
				{
					showDeviceList();
				}
			});

			builder.setMessage(message);
		
			builder.create().show();
		}
	}

	private void showConnectionFailDialog(final WaltzDevice device, int error)
	{
		if(DEBUG)
			Log.d(TAG, "showConnectionFailDialog d:" + device);
			
		String name = device.getName();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.dialog_connection_fail);
		//builder.setMessage(getString(R.string.dialog_fail_to_connect) + " " + name);
		switch (error){
			case WaltzDevice.ACCESS_CODE_AUTHORITY_ERROR:
				builder.setMessage(getString(R.string.dialog_fail_to_connect) + " " + name);
				break;
			case WaltzDevice.REQUEST_TIMEOUT:
				builder.setMessage(getString(R.string.dialog_fail_to_connect) + " " + name);
				break;
			case WaltzDevice.AM_SERVER_CONNECTION_ERROR:
				builder.setMessage(name + " " + getString(R.string.cannot_connect) + " " + "AM Server");
				break;
			case WaltzDevice.AM_SERVER_PERMISSION_ERROR:
				builder.setMessage(getString(R.string.dialog_fail_to_connect) + " " + name + "(Permission)");
				break;
			case WaltzDevice.PORT_BLOCKING_ERROR:
				builder.setMessage(name + " " + getString(R.string.cannot_reach_am));
				break;
			case WaltzDevice.DNS_RESOLVING_ERROR:
				builder.setMessage(getString(R.string.cannot_resolve_dns));
				break;
			default:
				builder.setMessage(getString(R.string.dialog_fail_to_connect) + " " + name);
				break;
		}
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
				showDeviceList();
			}
		});

		builder.create().show();
	}

	private void showAccessCodeNotMatchDialog()
	{
		if(DEBUG)
			Log.d(TAG, "showAccessCodeNotMatchDialog");
			
		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.app_name);
		builder.setMessage(R.string.dialog_access_code_not_match);
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
			}
		});

		builder.create().show();
	}
	
//20160223 joyce add
	private void showAccessCodeLengthWrongDialog()
	{
		if(DEBUG)
			Log.d(TAG, "showAccessCodeLengthWrongDialog");

		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.app_name);
		builder.setMessage(R.string.dialog_access_code_length_wrong);
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
				showInputAccessCodeDialog(mCurrentDeviceIndex);
			}
		});

		builder.create().show();
	}

	private void showStartAllowJoinDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		builder.setIcon(R.drawable.sensor_other);
		builder.setTitle(R.string.sensor_allow_join);
		builder.setMessage(R.string.sensor_start_allow_join);
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
			}
		});

		builder.create().show();
	}

	// starting the service to register with GCM
	private void registerGCM() {
		Intent intent = new Intent(this, GcmIntentService.class);
		intent.putExtra(GcmIntentService.KEY, GcmIntentService.RESTRICTIONS_SERVICE);
		startService(intent);
	}

	private void subscribeGcmChannels(ArrayList<String> channels){
		for(String channel : channels){
			Intent intent = new Intent(this, GcmIntentService.class);
			intent.putExtra(GcmIntentService.KEY, GcmIntentService.SUBSCRIBE);
			intent.putExtra(GcmIntentService.TOPIC, channel);
			startService(intent);
		}
	}

	private void unsubscribeGcmChannel(String channel){
		Intent intent = new Intent(this, GcmIntentService.class);
		intent.putExtra(GcmIntentService.KEY, GcmIntentService.UNSUBSCRIBE);
		intent.putExtra(GcmIntentService.TOPIC, channel);
		startService(intent);
	}

	private void unsubscribeAllGcmChannel(){
		String deviceListText = mPreferences.getString("waltzone_local_data", null);
		if(deviceListText == null) return;
		try
		{
			StringReader stringReader = new StringReader(deviceListText);
			JsonReader reader = new JsonReader(stringReader);

			reader.beginArray();

			while(reader.hasNext())
			{
				String deviceSerial = null;

				reader.beginObject();

				while(reader.hasNext())
				{
					String name = reader.nextName();

					 if(name.equals("waltzone_serial"))
					{
						deviceSerial = reader.nextString();
					}
					else
					{
						reader.skipValue();
					}
				}
//				Log.e(TAG, "Unsubscribe: WALTZ_" + deviceSerial);
				unsubscribeGcmChannel("WALTZ_" + deviceSerial);
				reader.endObject();
			}

			reader.endArray();
			reader.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		catch(IllegalStateException e)
		{
			//e.printStackTrace();
		}
	}

	private boolean checkPlayServices() {
		GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
		int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (apiAvailability.isUserResolvableError(resultCode)) {
				apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				if(DEBUG)
					Log.i(TAG, "This device is not supported. Google Play Services not installed!");
				Crashlytics.logException(new Exception("This device is not supported. Google Play Services not installed!"));
				Toast.makeText(getApplicationContext(), "This device is not supported. Google Play Services not installed!", Toast.LENGTH_LONG).show();
				finish();
			}
			return false;
		}
		return true;
	}

	///
	///WPS
	///
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onShowWPSParingDialog(WPSStartParingEvent event)
	{
		if(DEBUG)
			Log.d(TAG, "onShowWPSParingDialog");

		WaltzDevice currentDevice = null;
		mEventDevice = event.getDevice();
		if(mEventDevice==null)
			return;

		if(mCurrentDeviceIndex < 0)
		{
			mCurrentDeviceIndex = MainApplication.getWaltzDeviceIndex(mEventDevice);
			
			if(mCurrentDeviceIndex>=0)
				currentDevice = mEventDevice;
		}
		else
		{
			currentDevice = MainApplication.getWaltzDevice(mCurrentDeviceIndex);
		}

		if(currentDevice == null)
			return;

		mIsEndPrepareParing = false;
		mIsHasSeenWPSParingAlert = false;
		long currentTime = System.currentTimeMillis();
		long previousJoinTime = MainApplication.getWPSParingTimestamp(mEventDevice.getSerial());
		mPrepareWPSParingCount = (int) ((WPS_PARING_BUFFER - currentTime + previousJoinTime) / 1000);
		if(mPrepareWPSParingCount <= 0) {
			mWPSParingTimeLeft = (int) ((WPS_PARING_TIME - currentTime + previousJoinTime) / 1000);
			mIsEndPrepareParing = true;
		}
		showWPSParingDialog(mEventDevice);
		startPrepareWPSParingTimer();
	}

	private void startPrepareWPSParingTimer()
	{
		if(DEBUG)
			Log.d(TAG, "startPrepareWPSParingTimer");

		mPrepareWPSParingHandler.removeCallbacks(mPrepareWPSParingRunnable);
		mPrepareWPSParingHandler.postDelayed(mPrepareWPSParingRunnable, 1000);
	}

	private void stopPrepareWPSParingTimer()
	{
		if(DEBUG)
			Log.d(TAG, "stopPrepareWPSParingTimer");

		mPrepareWPSParingHandler.removeCallbacks(mPrepareWPSParingRunnable);
	}

	private Runnable mPrepareWPSParingRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if(mPrepareWPSParingCount > 1) {
				mPrepareWPSParingCount--;
				updatePrepareWPSParingCount();
				mPrepareWPSParingHandler.postDelayed(mPrepareWPSParingRunnable, 1000);
			}
			else {
				stopPrepareWPSParingTimer();

				if(mEventDevice==null)
					return;

				long currentTime = System.currentTimeMillis();
				long previousWPSParingTime = MainApplication.getWPSParingTimestamp(mEventDevice.getSerial());
				mWPSParingTimeLeft = (int)((WPS_PARING_TIME - currentTime + previousWPSParingTime)/1000);
				updateWPSParingTimeLeft();
				startWPSParingTimeLeftTimer();
			}
		}
	};

	private void startWPSParingTimeLeftTimer()
	{
		if(DEBUG)
			Log.d(TAG, "startWPSParingTimeLeftTimer");

		mWPSParingTimeLeftHandler.removeCallbacks(mWPSParingTimeLeftRunnable);
		mWPSParingTimeLeftHandler.postDelayed(mWPSParingTimeLeftRunnable, 1000);
	}

	private void stopWPSParingTimeLeftTimer()
	{
		if(DEBUG)
			Log.d(TAG, "stopWPSParingTimeLeftTimer");

		mWPSParingTimeLeftHandler.removeCallbacks(mWPSParingTimeLeftRunnable);
	}

	private Runnable mWPSParingTimeLeftRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if(DEBUG)
				Log.d(TAG, "mAllowJoinTimeLeftRunnable run");

			if (mWPSParingTimeLeft > 1) {
				mWPSParingTimeLeft--;
				updateWPSParingTimeLeft();
				mWPSParingTimeLeftHandler.postDelayed(mWPSParingTimeLeftRunnable, 1000);
			}
			else
				updateWPSParingTimeOver();
		}
	};

	private void updatePrepareWPSParingCount()
	{
		mWPSParingText.post(new Runnable(){
			@Override
			public void run(){
				mWPSParingText.setText(String.format(getString(R.string.notification_system_prepare_wps_paring), mPrepareWPSParingCount));}
		});
	}

	private void updateWPSParingTimeLeft()
	{
		mWPSParingText.post(new Runnable(){
			@Override
			public void run(){
				mWPSParingText.setText(String.format(getString(R.string.notification_system_wps_still_in_paring), mWPSParingTimeLeft));}
		});
	}

	private void updateWPSParingTimeOver()
	{
		mWPSParingText.post(new Runnable(){
			@Override
			public void run(){
				mWPSParingText.setText(getString(R.string.notification_system_end_wps_paring));}
		});
	}
	/*
	public void showWpsAlertDialogIfNeed(WaltzDevice device)
	{
		if(!mIsHasSeenWPSParingAlert) {
			WaltzDevice currentDevice = null;
			if(mCurrentDeviceIndex > 0)
				currentDevice = mDeviceList.get(mCurrentDeviceIndex);

			if((currentDevice==null) || (device==null))
				return;

			if(!currentDevice.equals(device))
				return;

			long currentTime = System.currentTimeMillis();
			long previousJoinTime = MainApplication.getWPSParingTimestamp(device.getSerial());

			mWPSParingTimeLeft = 0;
			mPrepareWPSParingCount = (int) ((WPS_PARING_BUFFER - currentTime + previousJoinTime) / 1000);
			if(mPrepareWPSParingCount <= 0) {
				mWPSParingTimeLeft = (int) ((WPS_PARING_TIME - currentTime + previousJoinTime) / 1000);
				mIsEndPrepareParing = true;
			}

			if(mPrepareWPSParingCount > 0){
				showWPSParingDialog(currentDevice);
				startPrepareWPSParingTimer();
			}
			else if (mWPSParingTimeLeft > 0){
				showWPSParingDialog(currentDevice);
				startWPSParingTimeLeftTimer();
			}
		}
	}
*/
	private void showWPSParingDialog(WaltzDevice device)
	{
		if(DEBUG)
			Log.d(TAG, "showWPSParingDialog");

		if(mWPSDialog!=null)
		{
			mWPSDialog.dismiss();
		}
		LayoutInflater inflater = LayoutInflater.from(this);
		View mDialogView = inflater.inflate(R.layout.sensor_allow_join, null);

		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WaltzDialog);

		mWPSParingText = (TextView) mDialogView.findViewById(R.id.sensor_allow_join_text);
		TextView mDeviceNameText = (TextView) mDialogView.findViewById(R.id.device_name);

		mDeviceNameText.setText(getString(R.string.device) + "("+ device.getName() + ")");
		if (mIsEndPrepareParing)
			mWPSParingText.setText(String.format(getString(R.string.notification_system_wps_still_in_paring), mWPSParingTimeLeft));//mWPSParingCount));
		else
			mWPSParingText.setText(String.format(getString(R.string.notification_system_prepare_wps_paring), mPrepareWPSParingCount));//

		builder.setIcon(R.drawable.seek_thumb_normal);
		builder.setTitle(R.string.sensor_allow_join);
		builder.setView(mDialogView);
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int id)
			{
				//stopWPSParingTimer();
				mIsHasSeenWPSParingAlert = true;
				stopWPSParingTimeLeftTimer();
			}
		});

		mWPSDialog = builder.create();
		mWPSDialog.show();
	}
}
