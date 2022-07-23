package com.wulala.myapplicationmediacodecdemo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MyExtractor {

    MediaExtractor mediaExtractor;

    int videoTrackId = 0;
    int audioTrackId = 0;
    MediaFormat videoFormat = null;
    MediaFormat audioFormat = null;

    long curSampleTimeUS;
    int curSampleFlags;

    public long getSampleTime() {
        return curSampleTimeUS;
    }

    public int getSampleFlags() {
        return curSampleFlags;
    }

    public MyExtractor(String fileName, Context context) {
        mediaExtractor = new MediaExtractor();

        try {
            AssetFileDescriptor assetFileDescriptor = context.getAssets().openFd(fileName);
            mediaExtractor.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        int trackNumber = mediaExtractor.getTrackCount();
        for (int trackIdx = 0; trackIdx < trackNumber; trackIdx++) {

            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(trackIdx);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if(mime.startsWith("video")){
                videoTrackId = trackIdx;
                videoFormat = mediaFormat;
            }else if(mime.startsWith("audio")){
                audioTrackId = trackIdx;
                audioFormat = mediaFormat;
            }
        }
    }

    public void selectTrack(int trackId){
        mediaExtractor.selectTrack(trackId);
    }

    // 读取一帧数据
    public int readBuffer(ByteBuffer buffer){

        // 清空数据
        buffer.clear();

        // mediaExtractor.selectTrack(video ? videoTrackId : audioTrackId);
        int sampleSize = mediaExtractor.readSampleData(buffer, 0);

        if(sampleSize < 0){
            return -1;
        }

        // 记录当前时间戳
        curSampleTimeUS = mediaExtractor.getSampleTime();

        // 记录当前帧的标志位
        curSampleFlags = mediaExtractor.getSampleFlags();

        // 进入下一帧
        mediaExtractor.advance();

        return sampleSize;

    }

    public MediaFormat getVideoFormat() {
        return videoFormat;
    }

    public MediaFormat getAudioFormat() {
        return audioFormat;
    }

    public int getVideoTrackId() {
        return videoTrackId;
    }

    public int getAudioTrackId() {
        return audioTrackId;
    }

    public void release(){
        mediaExtractor.release();
    }
}
