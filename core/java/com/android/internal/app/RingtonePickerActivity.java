/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.app;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The {@link RingtonePickerActivity} allows the user to choose one from all of the
 * available ringtones. The chosen ringtone's URI will be persisted as a string.
 *
 * @see RingtoneManager#ACTION_RINGTONE_PICKER
 */
public final class RingtonePickerActivity extends AlertActivity implements
        AdapterView.OnItemSelectedListener, Runnable, DialogInterface.OnClickListener,
        AlertController.AlertParams.OnPrepareListViewListener {

    private static final String TAG = "RingtonePickerActivity";

    private static final int DELAY_MS_SELECTION_PLAYED = 300;
    
    private RingtoneManager mRingtoneManager;
    
    private Cursor mCursor;
    private Handler mHandler;

    /** The position in the list of the 'Silent' item. */
    private int mSilentPos = -1;
    
    /** The position in the list of the 'Default' item. */
    private int mDefaultRingtonePos = -1;

    /** The position in the list of the last clicked item. */
    private int mClickedPos = -1;
    
    /** The position in the list of the ringtone to sample. */
    private int mSampleRingtonePos = -1;

    /** Whether this list has the 'Silent' item. */
    private boolean mHasSilentItem;
    
    /** The Uri to place a checkmark next to. */
    private Uri mExistingUri;
    
    /** The number of static items in the list. */
    private int mStaticItemCount;
    
    /** Whether this list has the 'Default' item. */
    private boolean mHasDefaultItem;
    
    /** The Uri to play when the 'Default' item is clicked. */
    private Uri mUriForDefaultItem;

    /**
     * A Ringtone for the default ringtone. In most cases, the RingtoneManager
     * will stop the previous ringtone. However, the RingtoneManager doesn't
     * manage the default ringtone for us, so we should stop this one manually.
     */
    private Ringtone mDefaultRingtone;
    

    private StorageManager mStorageManager = null;
    private DialogInterface.OnClickListener mRingtoneClickListener =
            new DialogInterface.OnClickListener() {

        /*
         * On item clicked
         */
        public void onClick(DialogInterface dialog, int which) {
            // Save the position of most recently clicked item
            mClickedPos = which;
            
            // Play clip
            playRingtone(which, 0);
        }
        
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        Intent intent = getIntent();

        /*
         * Get whether to show the 'Default' item, and the URI to play when the
         * default is clicked
         */
        mHasDefaultItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        mUriForDefaultItem = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI);
        if (mUriForDefaultItem == null) {
            mUriForDefaultItem = Settings.System.DEFAULT_RINGTONE_URI;
        }
        
        // Get whether to show the 'Silent' item
        mHasSilentItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        
        // Give the Activity so it can do managed queries
        mRingtoneManager = new RingtoneManager(this);

        // Get whether to include DRM ringtones
        boolean includeDrm = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM,
                true);
        mRingtoneManager.setIncludeDrm(includeDrm);

        // Get whether to include External ringtones
        boolean includeExternal = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_EXTERNAL, true);
        mRingtoneManager.setIncludeExternal(includeExternal);
        // Get the types of ringtones to show
        int types = intent.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1);
        if (types != -1) {
            mRingtoneManager.setType(types);
        }
        
        mCursor = mRingtoneManager.getCursor();
        
        // The volume keys will control the stream that we are choosing a ringtone for
        setVolumeControlStream(mRingtoneManager.inferStreamType());

        // Get the URI whose list item should have a checkmark
        mExistingUri = intent
                .getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);

        final AlertController.AlertParams p = mAlertParams;
        p.mCursor = mCursor;
        p.mOnClickListener = mRingtoneClickListener;
        p.mLabelColumn = MediaStore.Audio.Media.TITLE;
        p.mIsSingleChoice = true;
        p.mOnItemSelectedListener = this;
        p.mPositiveButtonText = getString(com.android.internal.R.string.ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(com.android.internal.R.string.cancel);
        p.mPositiveButtonListener = this;
        p.mOnPrepareListViewListener = this;

        p.mTitle = intent.getCharSequenceExtra(RingtoneManager.EXTRA_RINGTONE_TITLE);
        if (p.mTitle == null) {
            p.mTitle = getString(com.android.internal.R.string.ringtone_picker_title);
        }
        
        setupAlert();
		if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            mStorageManager.registerListener(mStorageListener);
        }

    }
    
    public void onResume(){
    	super.onResume();
    	//add by yangqingan 2011-11-22 for NEWMS00132817 start
        Intent musicIntent = new Intent("com.android.music.musicservicecommand");
        musicIntent.putExtra("command", "pause");
        this.sendBroadcast(musicIntent);
      //add by yangqingan 2011-11-22 for NEWMS00132817 end
        IntentFilter filter = new IntentFilter(Intent.ACTION_UMS_CONNECTED);
        registerReceiver(usbReceiver, filter);
    }

    public void onPrepareListView(ListView listView) {
        
        if (mHasDefaultItem) {
            mDefaultRingtonePos = addDefaultRingtoneItem(listView);
            
            if (RingtoneManager.isDefault(mExistingUri)) {
                mClickedPos = mDefaultRingtonePos;
            }
        }
        
        if (mHasSilentItem) {
            mSilentPos = addSilentItem(listView);
            
            // The 'Silent' item should use a null Uri
            if (mExistingUri == null) {
                mClickedPos = mSilentPos;
            }
        }

        if (mClickedPos == -1) {
            mClickedPos = getListPosition(mRingtoneManager.getRingtonePosition(mExistingUri));
            //add by bug 10729 start 2012-2-17
            if(mClickedPos == -1){
               mClickedPos = getListPosition(mRingtoneManager.getDefaultRingtonePosition());
            }
            //add by bug 10729 end 2012-2-17
        }
        
        // Put a checkmark next to an item.
        mAlertParams.mCheckedItem = mClickedPos;
    }

    /**
     * Adds a static item to the top of the list. A static item is one that is not from the
     * RingtoneManager.
     * 
     * @param listView The ListView to add to.
     * @param textResId The resource ID of the text for the item.
     * @return The position of the inserted item.
     */
    private int addStaticItem(ListView listView, int textResId) {
        TextView textView = (TextView) getLayoutInflater().inflate(
                com.android.internal.R.layout.select_dialog_singlechoice, listView, false);
        textView.setText(textResId);
        listView.addHeaderView(textView);
        mStaticItemCount++;
        return listView.getHeaderViewsCount() - 1;
    }
    
    private int addDefaultRingtoneItem(ListView listView) {
        return addStaticItem(listView, com.android.internal.R.string.ringtone_default);
    }
    
    private int addSilentItem(ListView listView) {
        return addStaticItem(listView, com.android.internal.R.string.ringtone_silent);
    }
    
    /*
     * On click of Ok/Cancel buttons
     */
    public void onClick(DialogInterface dialog, int which) {
        boolean positiveResult = which == DialogInterface.BUTTON_POSITIVE;
        
        // Stop playing the previous ringtone
        mRingtoneManager.stopPreviousRingtone();
        
        if (positiveResult) {
            Intent resultIntent = new Intent();
            Uri uri = null;
            
            if (mClickedPos == mDefaultRingtonePos) {
                // Set it to the default Uri that they originally gave us
                uri = mUriForDefaultItem;
            } else if (mClickedPos == mSilentPos) {
                // A null Uri is for the 'Silent' item
                uri = null;
            } else {
                uri = mRingtoneManager.getRingtoneUri(getRingtoneManagerPosition(mClickedPos));
            }

            resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri);
            setResult(RESULT_OK, resultIntent);
        } else {
            setResult(RESULT_CANCELED);
        }

        getWindow().getDecorView().post(new Runnable() {
            public void run() {
                mCursor.close();
            }
        });

        finish();
    }
    
    /*
     * On item selected via keys
     */
    public void onItemSelected(AdapterView parent, View view, int position, long id) {
        playRingtone(position, DELAY_MS_SELECTION_PLAYED);
    }

    public void onNothingSelected(AdapterView parent) {
    }

    private void playRingtone(int position, int delayMs) {
        mHandler.removeCallbacks(this);
        mSampleRingtonePos = position;
        mHandler.postDelayed(this, delayMs);
    }
    
    public void run() {
        
        if (mSampleRingtonePos == mSilentPos) {
            mRingtoneManager.stopPreviousRingtone();
            return;
        }
        
        /*
         * Stop the default ringtone, if it's playing (other ringtones will be
         * stopped by the RingtoneManager when we get another Ringtone from it.
         */
        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            mDefaultRingtone.stop();
            mDefaultRingtone = null;
        }
        
        Ringtone ringtone;
        if (mSampleRingtonePos == mDefaultRingtonePos) {
            if (mDefaultRingtone == null) {
                mDefaultRingtone = RingtoneManager.getRingtone(this, mUriForDefaultItem);
            }
            ringtone = mDefaultRingtone;
            
            /*
             * Normally the non-static RingtoneManager.getRingtone stops the
             * previous ringtone, but we're getting the default ringtone outside
             * of the RingtoneManager instance, so let's stop the previous
             * ringtone manually.
             */
            mRingtoneManager.stopPreviousRingtone();
            
        } else {
            ringtone = mRingtoneManager.getRingtone(getRingtoneManagerPosition(mSampleRingtonePos));
        }
        
        if (ringtone != null) {
//        	if(null != mAlert){/*fixed CR<NEWMS00109311> by luning at 2011.11.17*/
//        		mAlert.getButton(BUTTON_POSITIVE).setEnabled(true);
//        	}
            /* ===== fixed CR<NEWMS00109311> by luning at 2011.11.17 begin =====*/
            if(ringtone.isError()){
                Toast.makeText(this, com.android.internal.R.string.audio_play_failed, Toast.LENGTH_LONG).show();   
                mAlert.getButton(BUTTON_POSITIVE).setEnabled(false);
            }else{
            	 mAlert.getButton(BUTTON_POSITIVE).setEnabled(true);
            	 ringtone.play();
            }
            /* ===== fixed CR<NEWMS00109311> by luning at 2011.11.17 end =====*/
           
        }
//        else {
//        	if(null != mAlert){/*fixed CR<NEWMS00109311> by luning at 2011.11.17*/
//        		mAlert.getButton(BUTTON_POSITIVE).setEnabled(false);
//        	}
//        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopAnyPlayingRingtone();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAnyPlayingRingtone();
      //add by yangqingan 2011-11-22 for NEWMS00132817 start
        Intent intent = new Intent("com.android.music.musicservicecommand");
        intent.putExtra("command", "play");
        this.sendBroadcast(intent);
      //add by yangqingan 2011-11-22 for NEWMS00132817 end
        unregisterReceiver(usbReceiver);
    }
	@Override
    protected void onDestroy() {
        if (mStorageManager != null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
        super.onDestroy();
    }

    private void stopAnyPlayingRingtone() {

        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            mDefaultRingtone.stop();
        }
        
        if (mRingtoneManager != null) {
            mRingtoneManager.stopPreviousRingtone();
        }
    }
    
    private int getRingtoneManagerPosition(int listPos) {
        return listPos - mStaticItemCount;
    }
    
    private int getListPosition(int ringtoneManagerPos) {
        
        // If the manager position is -1 (for not found), return that
        if (ringtoneManagerPos < 0) return ringtoneManagerPos;
        
        return ringtoneManagerPos + mStaticItemCount;
    }
    
    StorageEventListener mStorageListener = new StorageEventListener() {

        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            Log.i(TAG, "Received storage state changed notification that " +
                    path + " changed state from " + oldState +
                    " to " + newState);
            finish();
        }
    };

    BroadcastReceiver usbReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "UsbRecever---onReceive = " + intent.getAction());
			if (intent.getAction().equals(Intent.ACTION_UMS_CONNECTED)) {
				finish();
			}
		}
    };

}