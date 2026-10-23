package app.tvdetox.startup;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private TextView diagnostic;
    private EditText mapId;

    private interface Operation { void run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(21, 26, 35));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(40), dp(20), dp(40), dp(20));
        scroll.addView(content);
        setContentView(scroll);

        text(content, "Detox Startup", 28);
        text(content, "Designed for Android TV 11. Opens your saved Key Mapper shortcut once, about 20 seconds after a full restart. Google TV may appear briefly. Waking from standby is not a new boot.", 17);
        status = text(content, "", 20);
        text(content, "Key Mapper map ID", 17);
        mapId = new EditText(this);
        mapId.setId(1001);
        mapId.setSingleLine(true);
        mapId.setTextSize(18);
        mapId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mapId.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mapId.setHint("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
        mapId.setText(StartupBridge.uid(this));
        content.addView(mapId, new LinearLayout.LayoutParams(-1, dp(52)));

        button(content, "Paste copied map ID", () -> perform(() -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() != 1) {
                throw new IllegalArgumentException("Copy one Key Mapper map ID first.");
            }
            CharSequence value = clip.getItemAt(0).coerceToText(this);
            mapId.setText(StartupBridge.uidFromClipboard(value == null ? "" : value.toString()));
            diagnostic.setText("Copied ID filled in. Select Save ID next.");
        }));

        button(content, "Save ID — leaves startup OFF", () -> perform(() -> {
            StartupBridge.saveUid(this, mapId.getText().toString());
            mapId.setText(StartupBridge.uid(this));
            diagnostic.setText("Map ID saved. Automatic startup is OFF.");
        }));
        button(content, "Test saved shortcut once", () -> perform(() -> {
            StartupBridge.test(this);
            diagnostic.setText("Request sent once. Check that TV Detox opens. This test does not enable automatic startup.");
        }));
        button(content, "Enable automatic startup", () -> perform(() -> {
            StartupBridge.enable(this);
            diagnostic.setText("Startup is ON for the next full restart. Nothing is launched now.");
        }));
        button(content, "Disable automatic startup", () -> perform(() -> {
            StartupBridge.disable(this);
            diagnostic.setText("Startup is OFF. Any pending request has been cancelled.");
        }));
        text(content, "Key Mapper Accessibility and the saved map must remain enabled. Pausing Key Mapper alone does not stop this startup trigger. To stop it, select Disable automatic startup here, or disable this helper in TV Settings.", 16);
        diagnostic = text(content, StartupBridge.lastStatus(this), 15);
        refresh();
    }

    @Override public void onResume() {
        super.onResume();
        if (status != null) refresh();
    }

    private void perform(Operation operation) {
        try {
            operation.run();
        } catch (Exception error) {
            diagnostic.setText("Not completed: " + StartupBridge.message(error));
        }
        refresh();
    }

    private void refresh() {
        status.setText("Automatic startup: " + (StartupBridge.enabled(this) ? "ON" : "OFF"));
    }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(4));
        parent.addView(view);
        return view;
    }

    private void button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setOnClickListener((View view) -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
