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

package com.rusdelphi.batterywatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    public static String mWatchLevel = "?", mSmartphoneLevel = "?";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    public static void sendMessage(Context context, String param1) {
        Intent intent = new Intent(context, ListenerService.class);
        intent.setAction(ListenerService.ACTION_SM);
        intent.putExtra(ListenerService.ACTION_SM_PARAM, param1);
        context.startService(intent);
    }


    public static int dpToPx(int dp) {
        float density = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        /**
         * Handler to update the time periodically in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Bitmap mWatch, mSmartphone;

        Paint mBackgroundPaint;
        Paint mTextPaint, mMinutesPaint;
        Paint mRoundTextPaint;
        Paint mDataPaint;
        Paint mRoundDataPaint;
        Paint mSecondPaint;
        Paint mTickPaint;
        Paint mPaintOval;
        boolean mAmbient;

        Time mTime;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private BroadcastReceiver mBatteryLevelReceiver;
        MessageReceiver messageReceiver = new MessageReceiver();
        private boolean mIsRound;


        public class MessageReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                mSmartphoneLevel = intent.getStringExtra("message");
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setStatusBarGravity(Gravity.TOP | Gravity.CENTER)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.LEFT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_PERSISTENT)
                    .build());
            Resources resources = WatchFace.this.getResources();


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.digital_background)));

            //mTextPaint = new Paint();
            mTextPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), (R.color.digital_text)));
            mTextPaint.setTextSize(resources.getDimension(R.dimen.digital_text_size));
            mTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));


            mMinutesPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), (R.color.digital_text)));
            mMinutesPaint.setTextSize(resources.getDimension(R.dimen.minutes_text_size));

           /* mMinutesPaint.setTextSize(resources.getDimension(R.dimen.digital_text_size));
            mMinutesPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
            mMinutesPaint.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.digital_text)));
            mMinutesPaint.setAntiAlias(true);*/


            mRoundTextPaint = new Paint();
            mRoundTextPaint.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.digital_text)));
            mRoundTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
            mRoundTextPaint.setAntiAlias(true);
            mRoundTextPaint.setTextSize(resources.getDimension(R.dimen.digital_text_size_round));


            // mDataPaint = new Paint();
            mDataPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), (R.color.digital_text)));
            mDataPaint.setTextSize(25);

            mRoundDataPaint = new Paint();
            mRoundDataPaint.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.digital_text)));
            mRoundDataPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            mRoundDataPaint.setAntiAlias(true);
            mRoundDataPaint.setTextSize(20);


            //mSecondPaint = new Paint();
            mSecondPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
            mSecondPaint.setTextSize(40);

            mTickPaint = new Paint();
            mTickPaint.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.digital_yellow)));
            mTickPaint.setStrokeWidth(2.f);


            mPaintOval = new Paint();
            mPaintOval.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.digital_yellow)));
            mPaintOval.setStyle(Paint.Style.STROKE);
            mPaintOval.setStrokeWidth(4.f);
            mPaintOval.setAntiAlias(true);

            int iW = 30;
            int iH = 56;
            //Load image from resource
            mWatch = BitmapFactory.decodeResource(resources,
                    R.drawable.watch_white);
            //Scale to target size
            mWatch = Bitmap.createScaledBitmap(mWatch, iW, iH, true);
            mSmartphone = BitmapFactory.decodeResource(resources,
                    R.drawable.smartphone_white);
            mSmartphone = Bitmap.createScaledBitmap(mSmartphone, iW, iH, true);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = false;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                WatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
            }
            IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            mBatteryLevelReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent i) {
                    int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    mWatchLevel = new java.text.DecimalFormat("00")
                            .format((((float) level / (float) scale) * 100.0f)) + "%";
                    sendMessage(WatchFace.this, mWatchLevel);
                    sendMessage(WatchFace.this, "get_level");
                    //updateUI();
                }
            };
            WatchFace.this.registerReceiver(mBatteryLevelReceiver, batteryLevelFilter);
            IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
            LocalBroadcastManager.getInstance(WatchFace.this).registerReceiver(messageReceiver, messageFilter);


        }

        private void unregisterReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = false;
                WatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            }
            LocalBroadcastManager.getInstance(WatchFace.this).unregisterReceiver(messageReceiver);
            if (mBatteryLevelReceiver != null) {
                WatchFace.this.unregisterReceiver(mBatteryLevelReceiver);
                mBatteryLevelReceiver = null;
                // Log.d("main", "onDestroy unregisterReceiver");
            }


        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            //   Resources resources = WatchFace.this.getResources();
            mIsRound = insets.isRound();

//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//            float textSize = resources.getDimension(isRound
//                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);


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
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            // ������ ���
            if (mIsRound) {
                drawRoundCanvas(canvas, bounds);
                return;
            }


            //������ �����
            String textTime = String.format("%d:%02d", mTime.hour, mTime.minute);


            int xPos = (canvas.getWidth() / 2) - (int) mTextPaint.measureText(textTime.substring(0, 2));
            int yPos = dpToPx(77);

            canvas.drawText(textTime.substring(0, 2), xPos, yPos, mTextPaint);

            xPos = (canvas.getWidth() / 2);

            canvas.drawText(textTime.substring(3, 5), xPos, yPos, mMinutesPaint);

            //������ �����
            String textDate = mTime.format("%d") + " " + mTime.format("%b");
            canvas.drawText(textDate, dpToPx(29), dpToPx(106), mDataPaint);

            //������ ���� ������
            canvas.drawText(mTime.format("%a"), dpToPx(29), dpToPx(118), mDataPaint);


            if (shouldTimerBeRunning()) {
                //draw seconds with circle
                canvas.drawText(String.valueOf(mTime.second), dpToPx(130), dpToPx(116), mSecondPaint);
                final RectF oval = new RectF();
                float center_x, center_y;
                center_x = dpToPx(143);
                center_y = dpToPx(108);
                float radius = 31f;
                oval.set(center_x - radius,
                        center_y - radius,
                        center_x + radius,
                        center_y + radius);
                canvas.drawArc(oval, 270, 6 * mTime.second, false, mPaintOval);

                //draw charge levels
                canvas.drawBitmap(mWatch, dpToPx(29), dpToPx(132), null);
                canvas.drawText(mWatchLevel, dpToPx(50), dpToPx(151), mDataPaint);

                canvas.drawBitmap(mSmartphone, dpToPx(116), dpToPx(132), null);
                canvas.drawText(mSmartphoneLevel, dpToPx(136), dpToPx(151), mDataPaint);
            }
            // ������  ����� �� �����
            int w = bounds.width();
            int h = bounds.height();
            float centerX = w / 2f;
            float step = w / 16;
            for (int tickIndex = 1; tickIndex < 60; tickIndex++) {
                if (mTime.second == tickIndex)
                    mTickPaint.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.white)));
                else
                    mTickPaint.setColor(ContextCompat.getColor(getApplicationContext(), (R.color.digital_yellow)));

                if (tickIndex > 0 && tickIndex < 8)
                    canvas.drawLine(centerX + tickIndex * step, 0, centerX + tickIndex * step,
                            10, mTickPaint);
                if (tickIndex > 7 && tickIndex < 23)
                    canvas.drawLine(w, step + (tickIndex - 8) * step, w - 10, step + (tickIndex - 8) * step,
                            mTickPaint);
                if (tickIndex > 22 && tickIndex < 38)
                    canvas.drawLine(w - (step + (tickIndex - 23) * step), h, w - (step + (tickIndex - 23) * step), h - 10,
                            mTickPaint);
                if (tickIndex > 37 && tickIndex < 53)
                    canvas.drawLine(0, step + (tickIndex - 38) * step, 10, step + (tickIndex - 38) * step,
                            mTickPaint);
                if (tickIndex > 52 && tickIndex < 61)
                    canvas.drawLine(step + (tickIndex - 53) * step, 0, step + (tickIndex - 53) * step, 10,
                            mTickPaint);
            }
            // draw a triangle������ �����������
            Path path = new Path();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.moveTo(centerX - 10, 0);
            path.lineTo(centerX + 10, 0);
            path.lineTo(centerX, (float) (Math.sqrt(3) * 10));
            path.close();

            canvas.drawPath(path, mTickPaint);


        }

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


        private void drawRoundCanvas(Canvas canvas, Rect bounds) {
            //������ �����
            String textTime = String.format("%d:%02d", mTime.hour, mTime.minute);

            int xPos = (canvas.getWidth() / 2) - ((int) mRoundTextPaint.measureText(textTime) / 2);
            int yPos = dpToPx(75);

            canvas.drawText(textTime, xPos, yPos, mRoundTextPaint);

            //������ �����
            String textDate = mTime.format("%d") + " " + mTime.format("%b");
            canvas.drawText(textDate, dpToPx(35), dpToPx(96), mRoundDataPaint);

            //������ ���� ������
            canvas.drawText(mTime.format("%a"), dpToPx(35), dpToPx(109), mRoundDataPaint);


            if (shouldTimerBeRunning()) {
                //draw seconds with circle
                canvas.drawText(String.valueOf(mTime.second), dpToPx(130), dpToPx(109), mSecondPaint);
                final RectF oval = new RectF();
                float center_x, center_y;
                center_x = dpToPx(144);
                center_y = dpToPx(99);
                float radius = 30f;
                oval.set(center_x - radius,
                        center_y - radius,
                        center_x + radius,
                        center_y + radius);
                canvas.drawArc(oval, 270, 6 * mTime.second, false, mPaintOval);

                //draw charge levels
                canvas.drawBitmap(mWatch, dpToPx(50), dpToPx(132), null);
                canvas.drawText(mWatchLevel, dpToPx(71), dpToPx(156), mDataPaint);

                canvas.drawBitmap(mSmartphone, dpToPx(116), dpToPx(132), null);
                canvas.drawText(mSmartphoneLevel, dpToPx(137), dpToPx(156), mDataPaint);
            }
            // ����
            // Рисуем  циферблат
            int width = bounds.width();
            int height = bounds.height();
            float centerX = width / 2f;
            float centerY = height / 2f;
            float innerTickRadius = centerX - 20;
            float outerTickRadius = centerX;
            for (int tickIndex = 1; tickIndex < 60; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 60);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(centerX + innerX, centerY + innerY,
                        centerX + outerX, centerY + outerY, mTickPaint);

            }
            // draw a triangle������ �����������
            Path path = new Path();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.moveTo(centerX - 10, 0);
            path.lineTo(centerX + 10, 0);
            path.lineTo(centerX, (float) (Math.sqrt(3) * 10));
            path.close();

            canvas.drawPath(path, mTickPaint);


        }
    }
}
