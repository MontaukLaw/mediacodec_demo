package com.wulala.myapplicationmediacodecdemo;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MyDecoder {

    private static final String TAG = MyDecoder.class.getSimpleName();

    int packetCounter = 0;
    boolean foundHead = false;

    long presentationTime = 0;
    MediaFormat mVideoFormat;
    InputStream inputStream;

    public MyDecoder(MediaCodec mDecoder, MediaExtractor mMediaExtractor, MediaFormat _videoFormat, InputStream _inputStream, AppCompatActivity context) {

        mVideoFormat = _videoFormat;
        inputStream = _inputStream;

        try {
            // 读取文件
            inputStream = context.getResources().getAssets().open("test.h264");
            // 先读出头
            inputStream.read();
            inputStream.read();
            inputStream.read();
            inputStream.read();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        // API 21以上，通过异步模式进行解码
        // 设置异步解码回调
        mDecoder.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(MediaCodec mc, int inputBufferId) {

                // Log.d(TAG, "inputBufferId: " + inputBufferId);
                // 从解码器中拿到输入buffer，让用户填充数据
                ByteBuffer decoderInputBuffer = mDecoder.getInputBuffer(inputBufferId);
                boolean mVideoExtractorDone = false;

                byte[] readBytesArr = new byte[200000];

                readBytesArr[0] = 0;
                readBytesArr[1] = 0;
                readBytesArr[2] = 0;
                readBytesArr[3] = 1;
                packetCounter = 4;

                int b1 = 0, b2 = 0, b3 = 0, b4 = 0;

                do {
                    try {
                        byte b = (byte) inputStream.read();
                        readBytesArr[packetCounter] = b;

                        b1 = b2;
                        b2 = b3;
                        b3 = b4;
                        b4 = b;

                        packetCounter++;

                        if (b1 == 0 && b2 == 0 && b3 == 0 && b4 == 1) {

                            foundHead = true;
                            packetCounter = packetCounter - 4;

                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } while (!foundHead);  // foundHead == false

                foundHead = false;
                Log.d(TAG, "packetCounter: " + packetCounter);

                decoderInputBuffer.put(readBytesArr, 0, packetCounter);

                mDecoder.queueInputBuffer(
                        inputBufferId, 0,
                        packetCounter,
                        presentationTime,
                        0);

                packetCounter = 4;
                presentationTime = presentationTime + 30000;
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG: ");
                    codec.releaseOutputBuffer(index, false);
                    return;
                }
                /*
                    这个地方先暂时认为每一帧的间隔是30ms，正常情况下，需要根据实际的视频帧的时间标记来计算每一帧的时间点。
                    因为视频帧的时间点是相对时间，正常第一帧是0，第二帧比如是第5ms。
                    基本思路是：取出第一帧视频数据，记住当前时间点，然后读取第二帧视频数据，再用当前时间点减去第一帧时间点，看看相对时间是多少，有没有
                    达到第二帧自己带的相对时间点。如果没有，则sleep一段时间，然后再去检查。直到大于或等于第二帧自带的时间点之后，进行视频渲染。
                */
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                }

                codec.releaseOutputBuffer(index, true);

                //如果视频数据已经读到结尾，则调用MediaExtractor的seekTo，跳转到视频开头，并且重置解码器。
                boolean reset = ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);

                if (reset) {
                    Log.d(TAG, "End of file");
                    mMediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    mDecoder.flush();    // reset decoder state
                    mDecoder.start();
                }
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                codec.reset();
            }

            @Override
            public void onOutputFormatChanged(MediaCodec mc, MediaFormat format) {

                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
                mVideoFormat = format; // option B
            }

        });

    }
}
