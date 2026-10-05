package tw.techtarian.browser;

import android.app.assist.AssistStructure;
import android.os.CancellationSignal;
import android.service.autofill.*;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.util.Pair;

/** Standalone test APK service: no dependency on the target application's runtime. */
public class FixtureAutofillService extends AutofillService {
    private AutofillId username,password;private boolean trusted;
    private void visit(AssistStructure.ViewNode node){
        if("127.0.0.1".equals(node.getWebDomain())||"localhost".equals(node.getWebDomain()))trusted=true;
        if(node.getHtmlInfo()!=null&&node.getHtmlInfo().getAttributes()!=null){
            for(Pair<String,String> attr:node.getHtmlInfo().getAttributes()){
                if("name".equals(attr.first)&&"username".equals(attr.second))username=node.getAutofillId();
                if("name".equals(attr.first)&&"password".equals(attr.second))password=node.getAutofillId();
            }
        }
        for(int i=0;i<node.getChildCount();i++)visit(node.getChildAt(i));
    }
    @Override public void onFillRequest(FillRequest request,CancellationSignal cancellation,FillCallback callback){
        username=null;password=null;trusted=false;
        AssistStructure structure=request.getFillContexts().get(request.getFillContexts().size()-1).getStructure();
        for(int i=0;i<structure.getWindowNodeCount();i++)visit(structure.getWindowNodeAt(i).getRootViewNode());
        if(!trusted||username==null||password==null){callback.onSuccess(null);return;}
        RemoteViews row=new RemoteViews(getPackageName(),android.R.layout.simple_list_item_1);
        row.setTextViewText(android.R.id.text1,"QA saved login");
        Dataset data=new Dataset.Builder(row).setValue(username,AutofillValue.forText("qa@example.invalid")).setValue(password,AutofillValue.forText("qa-fixture-password")).build();
        callback.onSuccess(new FillResponse.Builder().addDataset(data).setSaveInfo(new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_USERNAME|SaveInfo.SAVE_DATA_TYPE_PASSWORD,new AutofillId[]{username,password}).build()).build());
    }
    @Override public void onSaveRequest(SaveRequest request,SaveCallback callback){callback.onSuccess();}
}
