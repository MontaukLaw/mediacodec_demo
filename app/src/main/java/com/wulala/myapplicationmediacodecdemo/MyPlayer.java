package com.wulala.myapplicationmediacodecdemo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public class MyPlayer {

    private static final String TAG = MyPlayer.class.getSimpleName();
    private MediaExtractor mMediaExtractor;
    private MediaFormat mMediaFormat;
    private String mMediaFormatMime;

    private Deque<Integer> mOutputtIndexQueue;
    private MediaCodec mMediaCodec;

    private Handler mHandler;
    private Runnable mRunnable;

    public void initMediaExtractor(Context context, String fileName) {

        mMediaExtractor = new MediaExtractor();

        try {
            AssetFileDescriptor assetFileDescriptor = context.getAssets().openFd(fileName);
            mMediaExtractor.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int mediaTrackIdx = 0; mediaTrackIdx < mMediaExtractor.getTrackCount(); mediaTrackIdx++) {
            mMediaFormat = mMediaExtractor.getTrackFormat(mediaTrackIdx);
            mMediaFormatMime = mMediaFormat.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, "mMediaFormatMime: " + mMediaFormatMime);
            if (mMediaFormatMime.startsWith("video/")) {
                mMediaExtractor.selectTrack(mediaTrackIdx);
                break;
            }
        }
    }

    private void decode() {
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mMediaFormatMime);
            mOutputtIndexQueue = new ArrayDeque<>();
            // 异步解码的回调方法
            mMediaCodec.setCallback(new MediaCodec.Callback() {

                // 解码器异步方法
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

                    ByteBuffer inputBuffer = null;
                    try {
                        inputBuffer = codec.getInputBuffer(index);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    if (inputBuffer != null) {
                        // 获取采样数据
                        int sampleSize = mMediaExtractor.readSampleData(inputBuffer, 0);
                        // 获取视频帧的时间戳
                        long presentationTimeUs = mMediaExtractor.getSampleTime();
                        // 获取视频帧的标志位, 是否为关键帧
                        int flag = mMediaExtractor.getSampleFlags();

                        try {
                            // 将视频帧的信息放入解码器的inputBuffer队列
                            codec.queueInputBuffer(index, 0, sampleSize, presentationTimeUs, flag);
                            mMediaExtractor.advance();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {

                    // 解码器解码视
                    // 频帧完成回调在这里可以直接通过 codec.releaseOutputBuffer(index,true)把解码的内容显示出来，
                    // 但是这样做我们达不到控制视频播放速度的目的,
                    mOutputtIndexQueue.add(index);  // 把解码厚的OutputBufferAvailable的序列号用队列保存
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

                }
            });

            // mMediaCodec.configure(mMediaFormat, surface, null, 0);
            mMediaCodec.start();  // 解码器开始工作.
            // 这里, 只要有input数据, 就回自动解码, 出来的数据索引就会被加入队列.

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initDecodeThread() {
        mHandler = new Handler();
        mRunnable = new Runnable() {
            @Override
            public void run() {
                // 如果解码器的输出队列不为空
                if (!mOutputtIndexQueue.isEmpty()) {
                    // 释放解码器的buffer, 同时就是一个flush的过程.
                    mMediaCodec.releaseOutputBuffer(mOutputtIndexQueue.removeFirst(), true);
                }
                // 处于对帧率维持的需要, 因为解码的时间远比实际帧率要短, 不然还玩个屁对吧.
                //  mHandler.postDelayed(mRunnable, frameInterval);
            }
        };
    }

    public void releaseCodec() {
        mMediaCodec.stop();
        mMediaCodec.release();
    }

}
