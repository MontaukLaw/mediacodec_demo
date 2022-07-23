package com.wulala.myapplicationmediacodecdemo;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.TextureView;

import java.nio.ByteBuffer;

public abstract class BaseDecoder implements Runnable {
    final static int VIDEO = 1;
    final static int AUDIO = 2;

    // 等待时间
    final static int TIME_US = 1000;

    MediaFormat mediaFormat;
    MediaCodec mediaCodec;
    MyExtractor extractor;

    private boolean isDecoding = false;

    TextureView mTextureView;

    void configure() {
    }

    public BaseDecoder(Context context, String dataSource) {
        try {
            extractor = new MyExtractor(dataSource, context);

            //int type = decodeType();
            int type = 1;
            mediaFormat = (type == VIDEO ? extractor.getVideoFormat() : extractor.getAudioFormat());

            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            extractor.selectTrack(type == VIDEO ? extractor.getVideoTrackId() : extractor.getAudioTrackId());

            // 创建一个MediaCodec实例
            mediaCodec = MediaCodec.createDecoderByType(mime);

            configure();

            mediaCodec.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (isDecoding) {
                int inputBufferId = mediaCodec.dequeueInputBuffer(TIME_US);
                if (inputBufferId > 0) {
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
                    if (inputBuffer != null) {
                        int trackSize = extractor.readBuffer(inputBuffer);

                        if (trackSize > 0) {
                            mediaCodec.queueInputBuffer(inputBufferId, 0,
                                    trackSize,
                                    extractor.getSampleTime(),
                                    extractor.getSampleFlags());

                        } else {
                            // 文件读取结束EOF
                            mediaCodec.queueInputBuffer(
                                    inputBufferId,
                                    0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isDecoding = false;
                        }
                    }
                }

                boolean isFinished = handleOutputData(info);
                if (isFinished) {
                    break;
                }
            }

            decodeFinished();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected void decodeFinished() {
        try {

            isDecoding = false;

            // 释放MediaCodec
            mediaCodec.stop();
            mediaCodec.release();

            extractor.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    abstract boolean handleOutputData(MediaCodec.BufferInfo info);
}
