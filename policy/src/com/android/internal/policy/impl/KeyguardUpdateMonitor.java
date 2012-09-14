/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Telephony;
import static android.provider.Telephony.Intents.EXTRA_PHONE_ID;
import static android.provider.Telephony.Intents.EXTRA_PLMN;
import static android.provider.Telephony.Intents.EXTRA_SHOW_PLMN;
import static android.provider.Telephony.Intents.EXTRA_SHOW_SPN;
import static android.provider.Telephony.Intents.EXTRA_SPN;
import static android.provider.Telephony.Intents.SPN_STRINGS_UPDATED_ACTION;
import static android.provider.Telephony.Intents.EXTRA_NETWORK_TYPE;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;

import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.internal.R;
import com.google.android.collect.Lists;

import java.util.ArrayList;

/**
 * Watches for updates that may be interesting to the keyguard, and provides
 * the up to date information as well as a registration for callbacks that care
 * to be updated.
 *
 * Note: under time crunch, this has been extended to include some stuff that
 * doesn't really belong here.  see {@link #handleBatteryUpdate} where it shutdowns
 * the device, and {@link #getFailedAttempts()}, {@link #reportFailedAttempt()}
 * and {@link #clearFailedAttempts()}.  Maybe we should rename this 'KeyguardContext'...
 */
public class KeyguardUpdateMonitor {

    static private final String TAG = "KeyguardUpdateMonitor";
    static private final boolean DEBUG = true;

    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final Context mContext;

    private IccCard.State[]  mSimState;

    private boolean mKeyguardBypassEnabled;

    private boolean mDeviceProvisioned;

    private int mBatteryLevel;

    private int mBatteryStatus;

    private CharSequence[] mTelephonyPlmn;
    private CharSequence[] mTelephonySpn;
    private CharSequence[] networkType;
    private CharSequence mRadioType = null;  //add by liguxiang 08-25-11 for display radiotype(3G) on LockScreen

    private int mFailedAttempts = 0;

    private Handler mHandler;

    private ArrayList<InfoCallback> mInfoCallbacks = Lists.newArrayList();
    private ArrayList<SimStateCallback> mSimStateCallbacks = Lists.newArrayList();
    private ContentObserver mContentObserver;

    // messages for the handler
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_PHONE_STATE_CHANGED = 306;


    /**
     * When we receive a
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast,
     * and then pass a result via our handler to {@link KeyguardUpdateMonitor#handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result.
     */
    private static class SimArgs {

        public final IccCard.State simState;
        
        public final int phoneId ;

        private SimArgs(Intent intent) {
            if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            phoneId = intent.getIntExtra(IccCard.INTENT_KEY_PHONE_ID,0);
            String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
            if (IccCard.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                this.simState = IccCard.State.ABSENT;
            } else if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                this.simState = IccCard.State.READY;
            } else if (IccCard.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCard.INTENT_KEY_LOCKED_REASON);
                if (IccCard.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    this.simState = IccCard.State.PIN_REQUIRED;
                } else if (IccCard.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    this.simState = IccCard.State.PUK_REQUIRED;
                } else if (IccCard.INTENT_VALUE_LOCKED_NETWORK.equals(lockedReason)) {
                    this.simState = IccCard.State.NETWORK_LOCKED;
                } else if (IccCard.INTENT_VALUE_LOCKED_SIM.equals(lockedReason)) {
                    this.simState = IccCard.State.SIM_LOCKED;
                }else {
                    this.simState = IccCard.State.UNKNOWN;
                }
            }/* else if (IccCard.INTENT_VALUE_LOCKED_NETWORK.equals(stateExtra)) {
                this.simState = IccCard.State.NETWORK_LOCKED;
            }*/ else {
                this.simState = IccCard.State.UNKNOWN;
            }
        }

        public String toString() {
            return simState.toString();
        }
    }

    public KeyguardUpdateMonitor(Context context) {
        mContext = context;

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_TIME_UPDATE:
                        handleTimeUpdate();
                        break;
                    case MSG_BATTERY_UPDATE:
                        handleBatteryUpdate(msg.arg1,  msg.arg2);
                        break;
                    case MSG_CARRIER_INFO_UPDATE:
                        handleCarrierInfoUpdate(msg.arg1);
                        break;
                    case MSG_SIM_STATE_CHANGE:
                        handleSimStateChange((SimArgs) msg.obj);
                        break;
                    case MSG_RINGER_MODE_CHANGED:
                        handleRingerModeChange(msg.arg1);
                        break;
                    case MSG_PHONE_STATE_CHANGED:
                        handlePhoneStateChanged((String)msg.obj,msg.arg1);
                        break;
                }
            }
        };

        mKeyguardBypassEnabled = context.getResources().getBoolean(
                com.android.internal.R.bool.config_bypass_keyguard_if_slider_open);

        mDeviceProvisioned = Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.DEVICE_PROVISIONED, 0) != 0;

        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            mContentObserver = new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    mDeviceProvisioned = Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.DEVICE_PROVISIONED, 0) != 0;
                    if (mDeviceProvisioned && mContentObserver != null) {
                        // We don't need the observer anymore...
                        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
                        mContentObserver = null;
                    }
                    if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
                }
            };

            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.DEVICE_PROVISIONED),
                    false, mContentObserver);

            // prevent a race condition between where we check the flag and where we register the
            // observer by grabbing the value once again...
            mDeviceProvisioned = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DEVICE_PROVISIONED, 0) != 0;
        }

        // take a guess to start
        
        mBatteryStatus = BATTERY_STATUS_FULL;
        mBatteryLevel = 100;
        
		int numPhones = TelephonyManager.getPhoneCount();
		mTelephonyPlmn = new CharSequence[numPhones];
		mTelephonySpn = new CharSequence[numPhones];
		networkType = new CharSequence[numPhones];
		mSimState = new IccCard.State[numPhones];
		for (int i = 0; i < numPhones; i++) {
			mTelephonyPlmn[i] = getDefaultPlmn();
			mSimState[i] = IccCard.State.READY;
		}

        // setup receiver
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(SPN_STRINGS_UPDATED_ACTION + "0");
        filter.addAction(SPN_STRINGS_UPDATED_ACTION + "1");
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        context.registerReceiver(new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DEBUG) Log.d(TAG, "received broadcast " + action);

                if (Intent.ACTION_TIME_TICK.equals(action)
                        || Intent.ACTION_TIME_CHANGED.equals(action)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_TIME_UPDATE));
				} else if (SPN_STRINGS_UPDATED_ACTION.equals(action)
						|| (SPN_STRINGS_UPDATED_ACTION + "0").equals(action)
						|| (SPN_STRINGS_UPDATED_ACTION + "1").equals(action)) {
					int phoneID = intent.getIntExtra(EXTRA_PHONE_ID, 0);
					mTelephonyPlmn[phoneID] = getTelephonyPlmnFrom(intent);
					mTelephonySpn[phoneID] = getTelephonySpnFrom(intent);
					networkType[phoneID] = getTelephonyNetworkType(intent);
					Message m = mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE);
					m.arg1 = phoneID;
					mHandler.sendMessage(m);
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    final int pluggedInStatus = intent
                            .getIntExtra("status", BATTERY_STATUS_UNKNOWN);
                    int batteryLevel = intent.getIntExtra("level", 0);
                    final Message msg = mHandler.obtainMessage(
                            MSG_BATTERY_UPDATE,
                            pluggedInStatus,
                            batteryLevel);
                    mHandler.sendMessage(msg);
                } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(
                            MSG_SIM_STATE_CHANGE,
                            new SimArgs(intent)));
                } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                            intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
                } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    int sub=intent.getIntExtra(EXTRA_PHONE_ID, 0);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_PHONE_STATE_CHANGED,sub,0,state));
                }
            }
        }, filter);
    }

    protected void handlePhoneStateChanged(String newState,int sub) {
        if (DEBUG) Log.d(TAG, "handlePhoneStateChanged(" + newState + ")");
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onPhoneStateChanged(newState, sub);
        }
    }

    protected void handleRingerModeChange(int mode) {
        if (DEBUG) Log.d(TAG, "handleRingerModeChange(" + mode + ")");
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onRingerModeChanged(mode);
        }
    }

    /**
     * Handle {@link #MSG_TIME_UPDATE}
     */
    private void handleTimeUpdate() {
        if (DEBUG) Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onTimeChanged();
        }
    }

    /**
     * Handle {@link #MSG_BATTERY_UPDATE}
     */
    private void handleBatteryUpdate(int batteryStatus, int batteryLevel) {
        if (DEBUG) Log.d(TAG, "handleBatteryUpdate");
        if (isBatteryUpdateInteresting(batteryStatus, batteryLevel)) {
            mBatteryStatus = batteryStatus;
            mBatteryLevel = batteryLevel;
            final boolean pluggedIn = isPluggedIn(batteryStatus);;
            for (int i = 0; i < mInfoCallbacks.size(); i++) {
                mInfoCallbacks.get(i).onRefreshBatteryInfo(
                        shouldShowBatteryInfo(), pluggedIn, batteryLevel);
            }
        }
    }

    /**
     * Handle {@link #MSG_CARRIER_INFO_UPDATE}
     */
    private void handleCarrierInfoUpdate(int phoneId) {
        if (DEBUG) Log.d(TAG, "handleCarrierInfoUpdate: plmn = " + mTelephonyPlmn
            + ", spn = " + mTelephonySpn);

        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onRefreshCarrierInfo(mTelephonyPlmn[phoneId], mTelephonySpn[phoneId], phoneId);
        }
    }

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(SimArgs simArgs) {
        final IccCard.State state = simArgs.simState;

        if (DEBUG) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + "state resolved to " + state.toString());
        }

        if (state != IccCard.State.UNKNOWN /**&& state != mSimState[simArgs.phoneId]**/) {
            mSimState[simArgs.phoneId]= state;
            for (int i = 0; i < mSimStateCallbacks.size(); i++) {
                mSimStateCallbacks.get(i).onSimStateChanged(state,simArgs.phoneId);
            }
        }
    }

    /**
     * @param status One of the statuses of {@link android.os.BatteryManager}
     * @return Whether the status maps to a status for being plugged in.
     */
    private boolean isPluggedIn(int status) {
        return status == BATTERY_STATUS_CHARGING || status == BATTERY_STATUS_FULL;
    }

    private boolean isBatteryUpdateInteresting(int batteryStatus, int batteryLevel) {
        // change in plug is always interesting
        final boolean isPluggedIn = isPluggedIn(batteryStatus);
        final boolean wasPluggedIn = isPluggedIn(mBatteryStatus);
        final boolean stateChangedWhilePluggedIn =
            wasPluggedIn == true && isPluggedIn == true && (mBatteryStatus != batteryStatus);
        if (wasPluggedIn != isPluggedIn || stateChangedWhilePluggedIn) {
            return true;
        }

        // change in battery level while plugged in
        if (isPluggedIn && mBatteryLevel != batteryLevel) {
            return true;
        }

        if (!isPluggedIn) {
            // not plugged in and below threshold
            if (isBatteryLow(batteryLevel) && batteryLevel != mBatteryLevel) {
                return true;
            }
        }
        return false;
    }

    private boolean isBatteryLow(int batteryLevel) {
        return batteryLevel < LOW_BATTERY_THRESHOLD;
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_SHOW_PLMN, false)) {
            final String plmn = intent.getStringExtra(EXTRA_PLMN);
            if (plmn != null) {
                return plmn;
            } else {
                return getDefaultPlmn();
            }
        }
        return null;
    }

    /**
     * @return The default plmn (no service)
     */
    private CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(
                        R.string.lockscreen_carrier_default);
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_SHOW_SPN, false)) {
            final String spn = intent.getStringExtra(EXTRA_SPN);
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }
    
	private CharSequence getTelephonyNetworkType(Intent intent) {
		final String networkType = intent.getStringExtra(EXTRA_NETWORK_TYPE);
		if (networkType != null) {
			return networkType;
		}
		return "";
	}

    /**
     * Remove the given observer from being registered from any of the kinds
     * of callbacks.
     * @param observer The observer to remove (an instance of {@link ConfigurationChangeCallback},
     *   {@link InfoCallback} or {@link SimStateCallback}
     */
    public void removeCallback(Object observer) {
        mInfoCallbacks.remove(observer);
        mSimStateCallbacks.remove(observer);
    }

    /**
     * Callback for general information relevant to lock screen.
     */
    interface InfoCallback {
        void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn, int batteryLevel);
        void onTimeChanged();

        /**
         * @param plmn The operator name of the registered network.  May be null if it shouldn't
         *   be displayed.
         * @param spn The service provider name.  May be null if it shouldn't be displayed.
         */
        void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn,int phoneId);

        /**
         * Called when the ringer mode changes.
         * @param state the current ringer state, as defined in
         * {@link AudioManager#RINGER_MODE_CHANGED_ACTION}
         */
        void onRingerModeChanged(int state);

        /**
         * Called when the phone state changes. String will be one of:
         * {@link TelephonyManager#EXTRA_STATE_IDLE}
         * {@link TelephonyManager@EXTRA_STATE_RINGING}
         * {@link TelephonyManager#EXTRA_STATE_OFFHOOK
         */
        void onPhoneStateChanged(String newState,int sub);
        
        void onPhoneStateChanged(String newState);
    }

    /**
     * Callback to notify of sim state change.
     */
    interface SimStateCallback {
        void onSimStateChanged(IccCard.State simState,int phoneId);
    }

    /**
     * Register to receive notifications about general keyguard information
     * (see {@link InfoCallback}.
     * @param callback The callback.
     */
    public void registerInfoCallback(InfoCallback callback) {
        if (!mInfoCallbacks.contains(callback)) {
            mInfoCallbacks.add(callback);
        } else {
            Log.e(TAG, "Object tried to add another INFO callback", new Exception("Whoops"));
        }
    }

    /**
     * Register to be notified of sim state changes.
     * @param callback The callback.
     */
    public void registerSimStateCallback(SimStateCallback callback) {
        if (!mSimStateCallbacks.contains(callback)) {
            mSimStateCallbacks.add(callback);
        } else {
            Log.e(TAG, "Object tried to add another SIM callback", new Exception("Whoops"));
        }
    }

    public IccCard.State getSimState(int phoneId) {
        return mSimState[phoneId];
    }

    /**
     * Report that the user succesfully entered the sim pin so we
     * have the information earlier than waiting for the intent
     * broadcast from the telephony code.
     */
    public void reportSimPinUnlocked(int phoneId) {
        mSimState[phoneId] = IccCard.State.READY;
    }

    public boolean isKeyguardBypassEnabled() {
        return mKeyguardBypassEnabled;
    }

    public boolean isDevicePluggedIn() {
        return isPluggedIn(mBatteryStatus);
    }

    public boolean isDeviceCharged() {
        return mBatteryStatus == BatteryManager.BATTERY_STATUS_FULL
                || mBatteryLevel >= 100; // in case a particular device doesn't flag it
    }

    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public boolean shouldShowBatteryInfo() {
        return isPluggedIn(mBatteryStatus) || isBatteryLow(mBatteryLevel);
    }

    public CharSequence getTelephonyPlmn(int phoneId) {
        return mTelephonyPlmn[phoneId];
    }

    public CharSequence getTelephonySpn(int phoneId) {
        return mTelephonySpn[phoneId];
    }
    
	public CharSequence getNetworkType(int phoneId) {
		if (networkType[phoneId] == null) {

			return "";
		}
		return networkType[phoneId];
	}

    //add by liguxiang 08-25-11 for display radiotype(3G) on LockScreen begin
    public void setRadioType(int type){
        Log.d(TAG,"RadioType = " + type);
        switch (type) {
        case TelephonyManager.NETWORK_TYPE_UMTS:
        case TelephonyManager.NETWORK_TYPE_HSDPA:
        case TelephonyManager.NETWORK_TYPE_HSUPA:
        case TelephonyManager.NETWORK_TYPE_HSPA:
        case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
        case TelephonyManager.NETWORK_TYPE_IDEN:
            
                mRadioType = "3G";
            break;
        default:
                mRadioType = "";
        break;
        }
    }

    public CharSequence getRadioType(){
        return mRadioType;
    }
    //add by liguxiang 08-25-11 for display radiotype(3G) on LockScreen end

    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public int getFailedAttempts() {
        return mFailedAttempts;
    }

    public void clearFailedAttempts() {
        mFailedAttempts = 0;
    }

    public void reportFailedAttempt() {
        mFailedAttempts++;
    }
}