/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDayAndDatePaint;
        Paint mLineSeparator;
        Bitmap mWeatherBitmap;
        Paint mMaxTemperaturePaint;
        Paint mMinTemperaturePaint;
        String mMaxTemperatureString;
        String mMinTemperatureString;

        boolean mAmbient;
        Calendar mCalendar;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        float mSpacing;
        float mlineYOffset;
        int mWeatherBitmapWidth;
        int mWeatherBitmapHeight;




        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mSpacing = resources.getDimension(R.dimen.spacing_y_offset);
            mlineYOffset = mYOffset+ (2 * mSpacing);
            mWeatherBitmapHeight = (int) resources.getDimension(R.dimen.weather_image_height);
            mWeatherBitmapWidth = (int) resources.getDimension(R.dimen.weather_image_width);
            mMaxTemperatureString = "00";
            mMinTemperatureString = "00";

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_primary_text));

            mDayAndDatePaint = new Paint();
            mDayAndDatePaint = createTextPaint(resources.getColor(R.color.digital_secondary_text));

            mLineSeparator = new Paint();
            mLineSeparator.setColor(resources.getColor(R.color.digital_secondary_text));

            mCalendar = Calendar.getInstance();
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = new SimpleDateFormat("MMM d yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);

            mWeatherBitmap = BitmapFactory.decodeResource(getResources(),
                    R.mipmap.ic_launcher);

            mMaxTemperaturePaint = new Paint();
            mMaxTemperaturePaint = createTextPaint(resources.getColor(R.color.digital_primary_text));

            mMinTemperaturePaint = new Paint();
            mMinTemperaturePaint = createTextPaint(resources.getColor(R.color.digital_secondary_text));

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mDayAndDatePaint.setTextSize(textSize/2.5f);
            mMaxTemperaturePaint.setTextSize(textSize/2f);
            mMinTemperaturePaint.setTextSize(textSize/2f);



        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDayAndDatePaint.setAntiAlias(!inAmbientMode);
                    mLineSeparator.setAntiAlias(!inAmbientMode);
                    mMaxTemperaturePaint.setAntiAlias(!inAmbientMode);
                    mMinTemperaturePaint.setAntiAlias(!inAmbientMode);

                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Draw Time
            String text = String.format("%d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE));
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            // Draw Date
            float quarterWidth = bounds.width()/4f;
            String dayAndDate = mDayOfWeekFormat.format(now) + ", " + mDateFormat.format(now);
            float datePosition = (2f*quarterWidth)- mDayAndDatePaint.measureText(dayAndDate) / 2;
            canvas.drawText(dayAndDate, datePosition, mYOffset + mSpacing, mDayAndDatePaint);

            // Draw Line
            float eighthWidth = quarterWidth/2;
            float x1 = 3 * eighthWidth;
            float x2 = 5 * eighthWidth;
            canvas.drawLine(x1, mlineYOffset,x2,mlineYOffset,mLineSeparator);

            // Draw Weather Bitmap
            Bitmap drawableWeatherBitmap = Bitmap.createScaledBitmap(mWeatherBitmap,
                    mWeatherBitmapWidth, mWeatherBitmapHeight, true);
            float bitmapXOffset = x1 - drawableWeatherBitmap.getWidth()*1.5f;
            canvas.drawBitmap(drawableWeatherBitmap,bitmapXOffset,
                    mlineYOffset + 2*mSpacing - drawableWeatherBitmap.getHeight(), new Paint());

            // Draw Temperature Text
            String maxTemperatureString = mMaxTemperatureString + "\u00b0";
            String minTemperatureString = mMinTemperatureString + "\u00b0";

            canvas.drawText(maxTemperatureString, x1,
                    mlineYOffset + 2*mSpacing - (drawableWeatherBitmap.getHeight()/4f), mMaxTemperaturePaint);
            canvas.drawText(minTemperatureString, x1+mMaxTemperaturePaint.measureText(maxTemperatureString)*1.5f,
                    mlineYOffset + 2*mSpacing - (drawableWeatherBitmap.getHeight()/4f),mMinTemperaturePaint );

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}