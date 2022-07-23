package com.wulala.myapplicationmediacodecdemo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.wulala.myapplicationmediacodecdemo.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private ActivityMainBinding binding;

    private SurfaceTexture mSurfaceTexture;
    private MyExtractor myExtractor;

    private MediaExtractor mMediaExtractor;
    private MediaCodec mDecoder;
    private String mMimeType;
    private MediaFormat mVideoFormat = null;
    private int mVideoTrackIndex = -1;
    InputStream inputStream = null;

    private int mWidth = 720;
    private int mHeight = 1280;
    long presentationTime = 0;
    boolean foundHead = false;
    boolean initFlag = false;

    int packetCounter = 0;

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        TextureView mMovieView = binding.textureVideoView;

        mMovieView.setSurfaceTextureListener(this);

        // readTextFileStream();
        readH264FileStream();

        // initExtractor();

        // readH264FileStream();


        // mSurfaceTexture = mMovieView.getSurfaceTexture();

        // MyPlayer myPlayer = new MyPlayer();
        // myPlayer.initMediaExtractor(this, "q.mp4");
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }

    class Packet {

        int dateLen;
        byte[] data;


    }

    Packet packet;

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        // 拿到SurfaceTexture，作为视频解码器的数据输出。
        // 注意：在SurfaceTexture available之后，再进行解码。
        mSurfaceTexture = surface;    //开始解码
        // decode();
        // myExtractor = new MyExtractor("q.mp4", this);

        setDecoderCallback(mDecoder);
    }


    private void setDecoderCallback(MediaCodec mDecoder) {

        try {
            inputStream = getResources().getAssets().open("test.h264");
            // 先读出头
            inputStream.read();
            inputStream.read();
            inputStream.read();
            inputStream.read();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        mVideoFormat = new MediaFormat();

        mVideoFormat.setString(MediaFormat.KEY_MIME, "video/avc");
        mVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);

        mVideoFormat.setInteger(MediaFormat.KEY_WIDTH, 1920);
        mVideoFormat.setInteger(MediaFormat.KEY_HEIGHT, 1080);

        // API 21以上，通过异步模式进行解码
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

        // 设置渲染的颜色格式为Surface。
        mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        // 设置解码数据到指定的Surface上。
        mDecoder.configure(mVideoFormat, new Surface(mSurfaceTexture), null, 0);
        mDecoder.start();
    }

    private void readH264FileStream() {
        try {
            mDecoder = MediaCodec.createDecoderByType("video/avc");
            // setDecoderCallback(mDecoder);

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void readTextFileStream() {
        InputStream inputStream = null;
        Reader reader = null;
        BufferedReader bufferedReader = null;
        try {
            //得到资源中的asset数据流
            inputStream = getResources().getAssets().open("test.txt");
            reader = new InputStreamReader(inputStream);   // 字符流
            bufferedReader = new BufferedReader(reader);   // 缓冲流
            StringBuilder result = new StringBuilder();
            String temp;
            while ((temp = bufferedReader.readLine()) != null) {
                result.append(temp);
            }
            Log.i("MainActivity", "result:" + result);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    //初始化，通过MediaExtractor从视频文件中获取视频相关参数
    private void initExtractor() {
        try {
            // 不用注释
            mMediaExtractor = new MediaExtractor();

            // 根据本地的assets目录查找文件
            AssetFileDescriptor assetFileDescriptor = this.getAssets().openFd("q.mp4");  // "q.mp4"

            // 设置extractor的数据来源
            mMediaExtractor.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            // 获取视频的所有通道
            int trackCount = mMediaExtractor.getTrackCount();

            Log.d(TAG, "trackCount: " + trackCount);

            // 挨个通道查询media的类型
            for (int i = 0; i < trackCount; i++) {

                // 获取track的格式信息
                MediaFormat format = mMediaExtractor.getTrackFormat(i);

                // 获取格式的具体描述, string格式
                String mime = format.getString(MediaFormat.KEY_MIME);

                Log.d(TAG, "mime: " + mime);

                // 如果是视频track
                if (mime.startsWith("video/avc")) {

                    // 设置格式
                    mVideoFormat = format;
                    // 设置track索引
                    mVideoTrackIndex = i;
                    // 获取track的mime
                    mMimeType = mime;
                    // 不管音频部分
                    break;
                }
            }

            // 获取视频的宽高信息
            int width = mVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);

            mWidth = width;
            mHeight = height;

            Log.d(TAG, "width x height: " + mWidth + " x " + mHeight);

        } catch (Exception e) {
            e.printStackTrace();
            mMediaExtractor.release();
        }

        // 视频轨道格式的解析工作完成.
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void decode() {

        // 处理找不到文件的情况
        if (mVideoFormat == null) {
            Log.e(TAG, "decode: mVideoFormat null");
            return;
        }
        try {
            // 开始解码，需要选择视频轨道
            mMediaExtractor.selectTrack(mVideoTrackIndex);

            // 根据视频格式，创建对应的解码器
            mDecoder = MediaCodec.createDecoderByType(mMimeType);

            // API 21以上，通过异步模式进行解码
            mDecoder.setCallback(new MediaCodec.Callback() {

                @Override
                public void onInputBufferAvailable(MediaCodec mc, int inputBufferId) {

                    Log.d(TAG, "inputBufferId: " + inputBufferId);
                    // 从解码器中拿到输入buffer，让用户填充数据
                    ByteBuffer decoderInputBuffer = mDecoder.getInputBuffer(inputBufferId);
                    boolean mVideoExtractorDone = false;
                    while (!mVideoExtractorDone) {
                        // 从视频中读取数据
                        // 数据实际上是在这里被读取的.
                        // rtn的size就是实际数据大小
                        int size = mMediaExtractor.readSampleData(decoderInputBuffer, 0);

                        // 获取解码时间戳
                        long presentationTime = mMediaExtractor.getSampleTime();
                        //如果读取到数据，把buffer给回到解码器
                        if (size >= 0) {
                            // Log.d(TAG, "packet size: " + size);
                            mDecoder.queueInputBuffer(
                                    inputBufferId, 0,
                                    size,
                                    presentationTime,
                                    mMediaExtractor.getSampleFlags());

                            // Log.d(TAG, "sample flags: " + mMediaExtractor.getSampleFlags());
                        }

                        mVideoExtractorDone = !mMediaExtractor.advance();

                        if (mVideoExtractorDone) {
                            // 当然，如果取下一帧数据失败的时候，则也把buffer扔回去，带上end of stream标记，告知解码器，视频数据已经解析完
                            mDecoder.queueInputBuffer(
                                    inputBufferId,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }

                        if (size >= 0) {
                            break;
                        }
                    }
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
//                    try {
//                        Thread.sleep(30);
//                    } catch (InterruptedException e) {
//                    }
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

            // 设置渲染的颜色格式为Surface。
            mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

            // 设置解码数据到指定的Surface上。
            mDecoder.configure(mVideoFormat, new Surface(mSurfaceTexture), null, 0);
            mDecoder.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDecoder != null) {
            mDecoder.stop();
            mDecoder.release();
        }
    }
}