package tw.techtarian.browser.smoketests;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

/** Test-only external app. Never packaged in the browser APK or AAB. */
public class DeepLinkReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,64,32,32);
        TextView label=new TextView(this);label.setTextSize(20);
        String uri=getIntent().getDataString();
        label.setText("ChengJing link received\n"+uri+"\naction="+getIntent().getAction()+"\nextras="+(getIntent().getExtras()==null?0:getIntent().getExtras().size()));
        label.setContentDescription("deep-link-received:"+uri);layout.addView(label);
        Button back=new Button(this);back.setText("Return to browser");back.setOnClickListener(v->finish());layout.addView(back);
        setContentView(layout);
    }
}
