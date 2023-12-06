package com.netease.nertc.api.example;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.netease.nertc.api.example.R;
import com.netease.nertc.audiocall.AudioCallEntryActivity;
import com.netease.nertc.config.DemoDeploy;

/**
 * NERtc API-Example 主页面
 */

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DemoDeploy.requestPermissionsIfNeeded(this);//应用获取权限
        findViewById(R.id.ll_audio_call).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AudioCallEntryActivity.class);
                startActivity(intent);
            }
        });
    }
}