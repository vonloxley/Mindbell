/*
 * Copyright (C) 2010 Marc Schroeder.
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
package com.googlecode.mindbell.accessors;

import static com.googlecode.mindbell.MindBellPreferences.TAG;

import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.googlecode.mindbell.MindBell;
import com.googlecode.mindbell.MindBellMain;
import com.googlecode.mindbell.R;
import com.googlecode.mindbell.Scheduler;
import com.googlecode.mindbell.util.Utils;

/**
 * @author marc
 * 
 */
@SuppressLint("NewApi")
public class AndroidContextAccessor extends ContextAccessor implements AudioManager.OnAudioFocusChangeListener {
    public static final int KEYMUTEOFFHOOK = R.string.keyMuteOffHook;
    public static final int KEYMUTEWITHPHONE = R.string.keyMuteWithPhone;

    private static final int uniqueNotificationID = R.layout.bell;

    public static AndroidContextAccessor get(Context context) {
        return new AndroidContextAccessor(context);
    }

    private final Context context;

    private MediaPlayer mediaPlayer = null;
    private AudioManager audioMan = null;

    private PrefsAccessor prefs = null;

    private AndroidContextAccessor(Context context) {
        this.context = context;
    }

    @Override
    public void finishBellSound() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            MindBell.logDebug("Stopped ongoing player.");
        }
        mediaPlayer.release();
        if (android.os.Build.VERSION.SDK_INT >= 8 && audioMan != null) {
            audioMan.abandonAudioFocus(this);
            audioMan = null;
        }
        mediaPlayer = null;
    }

    @Override
    public int getAlarmMaxVolume() {
        AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioMan.getStreamMaxVolume(AudioManager.STREAM_ALARM);
    }

    @Override
    public int getAlarmVolume() {
        AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int alarmVolume = audioMan.getStreamVolume(AudioManager.STREAM_ALARM);
        Log.d(TAG, "Alarm volume is " + alarmVolume);
        return alarmVolume;
    }

    @Override
    public float getBellVolume() {
        if (prefs == null) {
            prefs = new AndroidPrefsAccessor(context);
        }
        float bellVolume = prefs.getBellVolume(getBellDefaultVolume());
        Log.d(TAG, "Bell volume is " + bellVolume);
        return bellVolume;
    }

    @Override
    public boolean isBellSoundPlaying() {
        return mediaPlayer != null;
    }

    @Override
    public boolean isPhoneMuted() {
        final AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioMan.getStreamVolume(AudioManager.STREAM_RING) == 0;
    }

    @Override
    public boolean isPhoneOffHook() {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    @Override
    public boolean isSettingMuteOffHook() {
        if (prefs == null) {
            prefs = new AndroidPrefsAccessor(context);
        }
        return prefs.isSettingMuteOffHook();
    }

    @Override
    public boolean isSettingMuteWithPhone() {
        if (prefs == null) {
            prefs = new AndroidPrefsAccessor(context);
        }
        return prefs.isSettingMuteWithPhone();
    }

    @Override
    public boolean isSettingVibrate() {
        if (prefs == null) {
            prefs = new AndroidPrefsAccessor(context);
        }
        return prefs.isSettingVibrate();
    }

    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
        case AudioManager.AUDIOFOCUS_LOSS:
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            finishBellSound();
            break;
        default:
            break;
        }
    }

    private void removeStatusNotification() {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(uniqueNotificationID);
    }

    @Override
    public void setAlarmVolume(int volume) {
        AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        Log.d(TAG, "Setting alarm volume to " + volume);
        audioMan.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0);

    }

    @Override
    public void showMessage(String message) {
        MindBell.logDebug(message);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void startBellSound(final Runnable runWhenDone) {
        // MindBell.logDebug("Starting bell sound");
        audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 8) {
            int result = audioMan.requestAudioFocus(this, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                vibrate(); // Not allowed to ring the bell, at least vibrate.
                if (runWhenDone != null) {
                    runWhenDone.run();
                }
                return;
            }
        }
        setAlarmVolume(getAlarmMaxVolume());
        float bellVolume = getBellVolume();
        MindBell.logDebug("Ringing bell with volume " + bellVolume);
        Uri bellUri = Utils.getResourceUri(context, R.raw.bell10s);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        mediaPlayer.setVolume(bellVolume, bellVolume);
        try {
            mediaPlayer.setDataSource(context, bellUri);
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    // MindBell.logDebug("Upon completion, originalVolume is " + originalVolume);
                    finishBellSound();
                    if (runWhenDone != null) {
                        runWhenDone.run();
                    }
                }
            });

            mediaPlayer.start();

            vibrate();

        } catch (IOException ioe) {
            Log.e(TAG, "Cannot set up bell sound", ioe);
            if (runWhenDone != null) {
                runWhenDone.run();
            }
        }
    }

    public void updateBellSchedule() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        PrefsAccessor prefs = new AndroidPrefsAccessor(sharedPrefs, context);
        if (prefs.isBellActive()) {
            Log.d(TAG, "Bell is active");
            if (prefs.doStatusNotification()) {
                updateStatusNotification(prefs);
            } else {
                removeStatusNotification();
            }
            Intent intent = new Intent(context, Scheduler.class);
            PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                sender.send();
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "Could not send: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "Bell is not active");
            removeStatusNotification(); // whatever the setting, no notification if bell is not active
        }
    }

    private void updateStatusNotification(PrefsAccessor prefs) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notif = new Notification(R.drawable.bell_status_active, "", System.currentTimeMillis());
        CharSequence contentTitle = context.getText(R.string.statusTitleBellActive);
        String contentText = context.getText(R.string.statusTextBellActive).toString();
        contentText = contentText.replace("_STARTTIME_", prefs.getDaytimeStartString()).replace("_ENDTIME_",
                prefs.getDaytimeEndString());
        Intent notificationIntent = new Intent(context, MindBellMain.class);
        PendingIntent contentIntent = PendingIntent
                .getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notif.setLatestEventInfo(context.getApplicationContext(), contentTitle, contentText, contentIntent);
        notif.flags |= Notification.FLAG_ONGOING_EVENT;
        notificationManager.notify(uniqueNotificationID, notif);
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (isSettingVibrate()) {
            long[] pattern = new long[] { 100, 200, 100, 600 };
            vibrator.vibrate(pattern, -1);
        } else {
            vibrator.vibrate(20);
        }
    }
}
