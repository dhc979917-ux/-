package com.example.myreader;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class VoiceFixtureActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 32, 32, 32);
        TextView count = new TextView(this);
        count.setText("action-count=0");
        count.setTextSize(20);
        TextView title = new TextView(this);
        title.setText("商品价格 123 元");
        title.setTextSize(24);
        content.addView(title);
        Button button = new Button(this);
        button.setText("Test action");
        button.setOnClickListener(view -> count.setText("action-count=1"));
        content.addView(button);
        content.addView(count);
        EditText password = new EditText(this);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setText("PRIVATE-PASSWORD-SENTINEL");
        content.addView(password);
        for (int index = 0; index < 35; index++) {
            TextView row = new TextView(this);
            row.setText("测试行 " + index + "：配送说明与商品详情");
            row.setTextSize(20);
            row.setPadding(0, 24, 0, 24);
            content.addView(row);
        }
        scroll.addView(content);
        setContentView(scroll);
    }
}
