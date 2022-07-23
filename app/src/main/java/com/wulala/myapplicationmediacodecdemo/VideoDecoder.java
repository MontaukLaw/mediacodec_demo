package com.wulala.myapplicationmediacodecdemo;

import android.content.Context;
import android.media.MediaCodec;
import android.util.Log;
import android.view.Surface;

public class VideoDecoder extends BaseDecoder {

    private final static String TAG = VideoDecoder.class.getSimpleName();

    public VideoDecoder(Context context, String dataSource) {
        super(context, dataSource);
    }

    @Override
    void configure() {
        mediaCodec.configure(mediaFormat, new Surface(mTextureView.getSurfaceTexture()), null, 0);
    }

    @Override
    boolean handleOutputData(MediaCodec.BufferInfo info) {

        // 获取到输出的buffer的索引
        int outputId = mediaCodec.dequeueOutputBuffer(info, TIME_US);

        if (outputId >= 0) {
            mediaCodec.releaseOutputBuffer(outputId, true);
        }

        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG, "handleOutputData: BUFFER_FLAG_END_OF_STREAM");
            return true;
        }

        return false;
    }
}
