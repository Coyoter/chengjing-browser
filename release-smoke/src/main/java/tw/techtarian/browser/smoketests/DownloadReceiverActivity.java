package tw.techtarian.browser.smoketests;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.InputStream;
import java.security.MessageDigest;

/** Test-only receiver verifies ACTION_VIEW grants and actual bytes, not just a filename. */
public class DownloadReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        TextView text=new TextView(this);text.setPadding(32,64,32,32);text.setTextSize(18);text.setText("Reading QA download");setContentView(text);
        new Thread(()->{
            String result;
            try(InputStream input=getContentResolver().openInputStream(getIntent().getData())){
                MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long size=0;int n;
                while((n=input.read(buffer))!=-1){hash.update(buffer,0,n);size+=n;}
                StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format("%02x",b&255));
                result="download-received:"+getIntent().getType()+":"+size+":"+hex;
            }catch(Exception error){result="download-error:"+error.getClass().getSimpleName();}
            String value=result;runOnUiThread(()->{text.setText(value);text.setContentDescription(value);});
        },"qa-download-reader").start();
    }
}
