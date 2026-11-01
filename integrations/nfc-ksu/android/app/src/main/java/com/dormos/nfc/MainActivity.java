package com.dormos.nfc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Android NFC dispatch starts this Activity on demand; no background service.
public final class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private NfcAdapter adapter;
    private TextView status;
    private volatile String writeScene;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long lastTap;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        adapter = NfcAdapter.getDefaultAdapter(this);
        status.setText(adapter == null ? "设备不支持 NFC" : "已就绪。点击场景写入 NFC 标签；下次刷卡直接触发。");
        acceptIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent); }
    @Override protected void onResume() { super.onResume(); if(writeScene != null) enableWriteMode(); }
    @Override protected void onPause() { if(adapter != null && writeScene != null) try {adapter.disableReaderMode(this);}catch(Exception ignored){} super.onPause(); }
    @Override protected void onDestroy() {worker.shutdown();super.onDestroy();}

    private void buildUi() {
        ScrollView sc = new ScrollView(this);
        LinearLayout c = new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(30,48,30,32);
        c.setBackgroundColor(Color.rgb(245,249,255));sc.addView(c);
        TextView title = new TextView(this);title.setText("DORM OS · NFC 场景");title.setTextSize(24);title.setTextColor(Color.rgb(22,80,164));c.addView(title);
        TextView desc = new TextView(this);desc.setText("无后台轮询。请选择写入场景，然后贴近 NTAG 标签。\n首次执行场景会请求 KernelSU Root 授权。");desc.setPadding(0,18,0,18);c.addView(desc);
        for(String scene:new String[]{"study","dingtalk","ncwu-check","dorm"}) {
            Button b = new Button(this);b.setText("写入："+scene);b.setAllCaps(false);c.addView(b);
            b.setOnClickListener(v->{if(adapter==null||!adapter.isEnabled()){status.setText("请开启 NFC");return;}writeScene=scene;status.setText("贴近标签以写入 "+scene);enableWriteMode();});
        }
        Button cancel = new Button(this);cancel.setText("取消写入");c.addView(cancel);
        cancel.setOnClickListener(v->{writeScene=null;if(adapter!=null)try{adapter.disableReaderMode(this);}catch(Exception ignored){} status.setText("写入已取消");});
        status = new TextView(this);status.setTextSize(15);status.setTextColor(Color.rgb(19,48,89));status.setPadding(0,24,0,0);c.addView(status);
        setContentView(sc);
    }
    private void enableWriteMode() {
        if(adapter==null)return;
        int flags=NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_NFC_V;
        try{adapter.enableReaderMode(this,this,flags,null);}catch(Exception ex){status.setText(ex.toString());}
    }
    @Override public void onTagDiscovered(Tag tag) {
        String scene=writeScene;if(scene==null)return;
        NdefMessage m=new NdefMessage(new NdefRecord[]{NdefRecord.createUri(Uri.parse("dormos://scene/"+scene))});
        String response;
        try{
            Ndef n=Ndef.get(tag);
            if(n!=null){n.connect();try{if(!n.isWritable()||n.getMaxSize()<m.toByteArray().length)throw new Exception("只读或容量不足");n.writeNdefMessage(m);}finally{n.close();}}
            else{NdefFormatable f=NdefFormatable.get(tag);if(f==null)throw new Exception("标签无法写入 NDEF");f.connect();try{f.format(m);}finally{f.close();}}
            writeScene=null;response="写入成功："+scene+"。重新刷卡触发。";
        }catch(Exception ex){response="写入失败："+ex.getMessage();}
        String r=response;runOnUiThread(()->{if(writeScene==null && adapter!=null)try{adapter.disableReaderMode(this);}catch(Exception ignored){}status.setText(r);});
    }
    private void acceptIntent(Intent i) {
        if(i==null||!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(i.getAction()))return;
        Tag tag=Build.VERSION.SDK_INT>=33?i.getParcelableExtra(NfcAdapter.EXTRA_TAG,Tag.class):i.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Uri uri=i.getData();
        if(tag==null||uri==null||!"dormos".equals(uri.getScheme())||!"scene".equals(uri.getHost()))return;
        String path=uri.getPath();if(path==null||path.length()<2)return;
        String scene=path.substring(1);
        if(!("study".equals(scene)||"dingtalk".equals(scene)||"ncwu-check".equals(scene)||"dorm".equals(scene)))return;
        if(System.currentTimeMillis()-lastTap<3000)return;lastTap=System.currentTimeMillis();
        status.setText("刷卡识别："+scene+"，请求 KernelSU 执行……");
        worker.execute(()->{
            String output;
            try{
                Process p=new ProcessBuilder("su","-c","/system/bin/sh /data/adb/modules/dormos_nfc/bin/dispatch.sh "+scene).redirectErrorStream(true).start();
                ByteArrayOutputStream buf=new ByteArrayOutputStream();
                try(InputStream in=p.getInputStream()){byte[] tmp=new byte[1024];int n;while((n=in.read(tmp))!=-1)if(buf.size()<16384)buf.write(tmp,0,Math.min(n,16384-buf.size()));}
                output="执行结果："+p.waitFor()+"\n"+buf.toString(StandardCharsets.UTF_8);
            }catch(Exception ex){output="执行失败，请检查 Root 授权和模块："+ex.getMessage();}
            String r=output;runOnUiThread(()->status.setText(r));
        });
    }
}
