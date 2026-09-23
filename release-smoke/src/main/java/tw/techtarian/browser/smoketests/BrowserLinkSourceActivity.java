package tw.techtarian.browser.smoketests;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A test-only stand-in for an app such as Dcard that opens the default browser. */
public class BrowserLinkSourceActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,64,32,32);
        TextView title=new TextView(this);title.setText("External source app");title.setContentDescription("external-link-source");layout.addView(title);
        Button open=new Button(this);open.setText("Open browser article");open.setContentDescription("open-browser-article");open.setAllCaps(false);open.setOnClickListener(v->{
            Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(getIntent().getStringExtra("url")));
            view.addCategory(Intent.CATEGORY_BROWSABLE);
            view.setComponent(new ComponentName("tw.techtarian.browser.qa","tw.techtarian.browser.MainActivity"));
            startActivity(view);
        });layout.addView(open);setContentView(layout);
    }
}
