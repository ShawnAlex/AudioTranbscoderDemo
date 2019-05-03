package com.example.audiotools;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.csf.lame4android.utils.FLameUtils;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.content.ContentValues.TAG;

public class TranscoderActivity extends Activity {

    private static final String TAG = "=TranscoderActivity=";
    public static final int NUM_CHANNELS = 1;
    public static final int SAMPLE_RATE = 16000;
    public static final int BITRATE = 128;
    private AudioRecord mRecorder;
    private short[] mBuffer;
    private final String startRecordingLabel = "Start recording";
    private final String stopRecordingLabel = "Stop recording";
    private boolean mIsRecording = false;
    private File mRawFile;
    private File mEncodedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transcoder);

        //MP3转AAC
        findViewById(R.id.btn_mp3_to_aac).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                    final TranscoderUtils transcoderUtils = TranscoderUtils.newInstance();
                    transcoderUtils.setEncodeType(MediaFormat.MIMETYPE_AUDIO_AAC);
                    transcoderUtils.setIOPath(path + "/text01.mp3", path + "/encode.aac");
                    transcoderUtils.prepare();
                    transcoderUtils.startAsync();
                    transcoderUtils.setOnCompleteListener(new AudioCodec.OnCompleteListener() {
                        @Override
                        public void completed() {
                            transcoderUtils.release();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "MP3->AAC" + e.getMessage());
                }
            }
        });

        //raw转MP3
        findViewById(R.id.raw_to_mp3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
//                File src = new File(dirPath+"testaac.raw");
//                File dst = new File(dirPath+"encode05.mp3");
//                rawToMp3(dst,src);
                    File src = new File(dirPath + "1515737143960.raw");
                    MediaPlayer.create(TranscoderActivity.this, Uri.fromFile(src)).start();
                }catch(Exception e){
                    Log.e(TAG,"=raw_to_mp3="+e.getMessage());
                }

            }
        });


        final AudioRecodeMediaCodec mediaCodec = new AudioRecodeMediaCodec();
        mediaCodec.initRecorder();
        /**
         * recorder aac
         * */
        final Button btn4 = (Button) findViewById(R.id.audiorecodemediacodec_btn);
        btn4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaCodec.mIsRecording) {
                    mediaCodec.stop();
                    btn4.setText("开始");
                } else {
                    mediaCodec.start();
                    btn4.setText("停止");
                }
            }
        });



        /**
        *after recoding raw file ,transcode to MP3
         * */
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        mBuffer = new short[bufferSize];
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        final Button button = (Button) findViewById(R.id.audio_record_btn);
        button.setText(startRecordingLabel);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                FLameUtils fLameUtils = new FLameUtils(NUM_CHANNELS, SAMPLE_RATE, BITRATE);
                if (!mIsRecording) {
                    button.setText(stopRecordingLabel);
                    mIsRecording = true;
                    mRecorder.startRecording();
                    mRawFile = getFile("raw");
                    startBufferedWrite(mRawFile);
                } else {
                    button.setText(startRecordingLabel);
                    mIsRecording = false;
                    mRecorder.stop();
                    mEncodedFile = getFile("mp3");
                    boolean result = fLameUtils.raw2mp3(mRawFile.getAbsolutePath(), mEncodedFile.getAbsolutePath());
                    if (result) {
                        Toast.makeText(TranscoderActivity.this, "Encoded to " + mEncodedFile.getName(), Toast.LENGTH_SHORT)
                                .show();
                    }
                }
            }
        });
    }
    public void rawToMp3(File dst, File src) {
        File srcFile = src;
        File dstFile = dst;
        try {
            MediaExtractor mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(new FileInputStream(srcFile).getFD());
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
                String mine = mediaFormat.getString(MediaFormat.KEY_MIME);
                Log.e(TAG, "=MINE=" + mine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        FLameUtils fLameUtils = new FLameUtils(NUM_CHANNELS, SAMPLE_RATE, BITRATE);
        boolean result = fLameUtils.raw2mp3(srcFile.getAbsolutePath(), dstFile.getAbsolutePath());
        if (result) {
            Toast.makeText(TranscoderActivity.this, "Encoded to " + dstFile.getName(), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void initRecorder() {

    }

    private void startBufferedWrite(final File file) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DataOutputStream output = null;
                try {
                    output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
                    while (mIsRecording) {
                        int readSize = mRecorder.read(mBuffer, 0, mBuffer.length);
                        for (int i = 0; i < readSize; i++) {
                            output.writeShort(mBuffer[i]);
                            Log.e(TAG, "=mBuffer=" + mBuffer[i]);
                        }
                    }
                } catch (IOException e) {
                    Toast.makeText(TranscoderActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    if (output != null) {
                        try {
                            output.flush();
                        } catch (IOException e) {
                            Toast.makeText(TranscoderActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        } finally {
                            try {
                                output.close();
                            } catch (IOException e) {
                                Toast.makeText(TranscoderActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
        }).start();
    }

    private File getFile(final String suffix) {
        Time time = new Time();
        time.setToNow();
        return new File(Environment.getExternalStorageDirectory(), time.format("%Y%m%d%H%M%S") + "." + suffix);
    }


}
