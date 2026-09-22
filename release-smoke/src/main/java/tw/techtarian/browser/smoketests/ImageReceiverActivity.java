package tw.techtarian.browser.smoketests;

import android.app.Activity;
import android.os.Bundle;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.security.MessageDigest;

/** Disposable receiver: proves another UID can read the image without sending it anywhere. */
public class ImageReceiverActivity extends Activity {
    private TextView result;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,120,32,80);
        Button paste=new Button(this);paste.setAllCaps(false);paste.setText("Paste QA image");
        result=new TextView(this);result.setText("Waiting for image");result.setTextSize(18);
        layout.addView(paste);layout.addView(result);setContentView(layout);
        paste.setOnClickListener(v->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            read(clipboard.hasPrimaryClip()?clipboard.getPrimaryClip().getItemAt(0).getUri():null);
        });
        if(Intent.ACTION_SEND.equals(getIntent().getAction()))read(getIntent().getParcelableExtra(Intent.EXTRA_STREAM));
    }
    private void read(Uri uri) {
        try {
            if(uri==null)throw new IllegalArgumentException("No image URI");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");int count=0,n;
            try(InputStream input=getContentResolver().openInputStream(uri)) {
                byte[] buffer=new byte[8192];while((n=input.read(buffer))!=-1){count+=n;digest.update(buffer,0,n);}
            }
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b & 255));
            String value="image-received:"+getContentResolver().getType(uri)+":"+count+":"+hex;
            result.setText(value);result.setContentDescription(value);
        }catch(Exception error){result.setText("Image read failed: "+error.getClass().getSimpleName());result.setContentDescription("image-read-failed");}
    }
}
