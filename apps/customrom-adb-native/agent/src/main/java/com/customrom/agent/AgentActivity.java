package com.customrom.agent;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AgentActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.rgb(5, 10, 19));

        TextView title = new TextView(this);
        title.setText("CUSTOMROM Agent");
        title.setTextSize(24f);
        title.setTextColor(Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView detail = new TextView(this);
        detail.setText("Fallback local para reativar ADB/Wireless debugging na TayTech após o boot.");
        detail.setTextSize(14f);
        detail.setTextColor(Color.LTGRAY);
        detail.setPadding(0, 18, 0, 24);
        detail.setGravity(Gravity.CENTER);
        root.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText(AdbRecovery.status(this));
        status.setTextSize(16f);
        status.setTextColor(Color.rgb(58, 214, 151));
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button apply = new Button(this);
        apply.setText("Ativar ADB agora");
        apply.setOnClickListener(v -> status.setText(AdbRecovery.apply(this)));
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        button.topMargin = 28;
        root.addView(apply, button);

        setContentView(root);
    }
}
