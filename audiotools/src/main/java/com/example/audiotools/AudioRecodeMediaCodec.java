package com.example.audiotools;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.text.format.Time;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.content.ContentValues.TAG;

/**
 * Created by user on 2018/1/14.
 */

public class AudioRecodeMediaCodec {
    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private final String mime = "audio/mp4a-latm";
    private int bitRate = 128000;
    private ByteBuffer[] inputBufferArray;
    private ByteBuffer[] outputBufferArray;
    private FileOutputStream fileOutputStream;

    public static final int SAMPLE_RATE = 44100;
    private AudioRecord mRecorder;
    private byte[] mBuffer;

    public void initRecorder() {
        if (mRecorder == null) {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            mBuffer = new byte[bufferSize];
            mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        }
    }

    public boolean mIsRecording = false;

    public void start() {
        if (!mIsRecording) {
            mIsRecording = true;
            initMediaCodec();
            mRecorder.startRecording();
            startBufferedWrite();
        } else {

        }
    }

    public void stop() {
        if (mIsRecording) {
            mIsRecording = false;
            mRecorder.stop();
            mMediaCodec.stop();
            mMediaCodec.release();
        } else {

        }
    }

    private void startBufferedWrite() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DataOutputStream output = null;
//                    output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
                while (mIsRecording) {
                    int readSize = mRecorder.read(mBuffer, 0, mBuffer.length);
                    encodeData(mBuffer);
                    Log.e(TAG, "=mBuffer=" + mBuffer.toString());
                }
            }
        }).start();
    }

    public void initMediaCodec() {
        try {
            File root = Environment.getExternalStorageDirectory();
            Time time = new Time();
            time.setToNow();
            File fileAAc = new File(root, time.format("%Y%m%d%H%M%S") + "aac.aac");
            if (!fileAAc.exists()) {
                fileAAc.createNewFile();
            }
            fileOutputStream = new FileOutputStream(fileAAc.getAbsoluteFile());
            mMediaCodec = MediaCodec.createEncoderByType(mime);
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, mime);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            /*
             * 第四个参数 编码的时候是MediaCodec.CONFIGURE_FLAG_ENCODE，解码的时候是0
             */
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //start（）后进入执行状态，才能做后续的操作
            mMediaCodec.start();
            //获取输入缓存，输出缓存
            inputBufferArray = mMediaCodec.getInputBuffers();
            outputBufferArray = mMediaCodec.getOutputBuffers();
            mBufferInfo = new MediaCodec.BufferInfo();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //计算PTS，实际上这个pts对应音频来说作用并不大，设置成0也是没有问题的
    private long computePresentationTime(long frameIndex) {
        return frameIndex * 90000 * 1024 / 44100;
    }
    //pts时间基数
    long presentationTimeUs = 0;
    public void encodeData(byte[] data) {
        //dequeueInputBuffer（time）需要传入一个时间值，-1表示一直等待，0表示不等待有可能会丢帧，其他表示等待多少毫秒
        int inputIndex = mMediaCodec.dequeueInputBuffer(-1);//获取输入缓存的index
        if (inputIndex >= 0) {
            ByteBuffer inputByteBuf = inputBufferArray[inputIndex];
            inputByteBuf.clear();
            inputByteBuf.put(data);//添加数据
            inputByteBuf.limit(data.length);//限制ByteBuffer的访问长度
            long pts = computePresentationTime(presentationTimeUs);
            mMediaCodec.queueInputBuffer(inputIndex, 0, data.length, pts, 0);//把输入缓存塞回去给MediaCodec
            //计算pts
            presentationTimeUs += 1;
        }

        int outputIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);//获取输出缓存的index
        while (outputIndex >= 0) {
            //获取缓存信息的长度
            int byteBufSize = mBufferInfo.size;
            //添加ADTS头部后的长度
            int bytePacketSize = byteBufSize + 7;

            ByteBuffer outPutBuf = outputBufferArray[outputIndex];
            outPutBuf.position(mBufferInfo.offset);
            outPutBuf.limit(mBufferInfo.offset + mBufferInfo.size);

            byte[] targetByte = new byte[bytePacketSize];
            //添加ADTS头部
            addADTStoPacket(targetByte, bytePacketSize);
            /*
            get（byte[] dst,int offset,int length）:ByteBuffer从position位置开始读，读取length个byte，并写入dst下
            标从offset到offset + length的区域
             */
            outPutBuf.get(targetByte, 7, byteBufSize);

            outPutBuf.position(mBufferInfo.offset);

            try {
                fileOutputStream.write(targetByte);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //释放
            mMediaCodec.releaseOutputBuffer(outputIndex, false);
            outputIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
        }
    }

    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}
