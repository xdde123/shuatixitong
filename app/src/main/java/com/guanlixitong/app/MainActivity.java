package com.guanlixitong.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.webkit.*;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private WebView webView;
    private String pendingBackupName = "管理系统备份.json";
    private String pendingBackupJson = "";
    private final int REQ_NOTIFICATION = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setTextZoom(100);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setHorizontalScrollBarEnabled(false);
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        createNotificationChannel();
    }

    public class AndroidBridge {
        @JavascriptInterface public void exportBackup(String json, String fileName){ runOnUiThread(() -> createBackup(json, fileName)); }
        @JavascriptInterface public void importBackup(){ runOnUiThread(MainActivity.this::openBackup); }
        @JavascriptInterface public void pickQuestionFile(){ runOnUiThread(MainActivity.this::openQuestionFile); }
        @JavascriptInterface public void scheduleReminder(int hour, int minute){ runOnUiThread(() -> scheduleDailyReminder(hour, minute)); }
    }

    private void createBackup(String json, String fileName){
        pendingBackupName = fileName; pendingBackupJson = json;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE,fileName); startActivityForResult(i,2001);
    }
    private void openBackup(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,2002); }
    private void openQuestionFile(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","text/plain"}); startActivityForResult(i,2003); }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){ super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null||data.getData()==null)return; Uri uri=data.getData(); try{
        if(requestCode==2001){ String json=pendingBackupJson; try(OutputStream os=getContentResolver().openOutputStream(uri)){os.write(json.getBytes(StandardCharsets.UTF_8));} Toast.makeText(this,"备份已保存",Toast.LENGTH_SHORT).show(); }
        else if(requestCode==2002){ String text=readAll(uri); js("receiveNativeBackup("+JSONObject.quote(text)+")"); }
        else if(requestCode==2003){ String name=getFileName(uri); String text=extractQuestionText(uri,name); js("receiveImportedQuestionText("+JSONObject.quote(name)+","+JSONObject.quote(text)+")"); }
    }catch(Exception e){Toast.makeText(this,"文件处理失败："+e.getMessage(),Toast.LENGTH_LONG).show();}}

    private String readAll(Uri uri)throws Exception{try(InputStream is=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=is.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private String getFileName(Uri uri){String name="题库"; try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(idx>=0)name=c.getString(idx);}}catch(Exception ignored){}return name;}
    private String extractQuestionText(Uri uri,String name)throws Exception{String lower=name.toLowerCase(); if(lower.endsWith(".pdf")){try(InputStream is=getContentResolver().openInputStream(uri); PDDocument doc=PDDocument.load(is)){return new PDFTextStripper().getText(doc);}} if(lower.endsWith(".docx"))return readDocx(uri); return readAll(uri);}
    private String readDocx(Uri uri)throws Exception{try(InputStream raw=getContentResolver().openInputStream(uri); ZipInputStream zis=new ZipInputStream(raw)){ZipEntry e;while((e=zis.getNextEntry())!=null){if("word/document.xml".equals(e.getName())){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=zis.read(b))!=-1)out.write(b,0,n);String xml=out.toString("UTF-8");return xml.replaceAll("<w:p[^>]*>","\n").replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").replace("\n ","\n");}}}return "";}
    private void js(String code){webView.post(() -> webView.evaluateJavascript("javascript:"+code,null));}

    private void createNotificationChannel(){if(android.os.Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("study_reminder","每日学习提醒",NotificationManager.IMPORTANCE_DEFAULT);c.setDescription("管理系统每日学习提醒");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private void scheduleDailyReminder(int hour,int minute){if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATION);} AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);Intent i=new Intent(this,ReminderReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(this,3001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);java.util.Calendar c=java.util.Calendar.getInstance();c.set(java.util.Calendar.HOUR_OF_DAY,hour);c.set(java.util.Calendar.MINUTE,minute);c.set(java.util.Calendar.SECOND,0);if(c.getTimeInMillis()<=System.currentTimeMillis())c.add(java.util.Calendar.DAY_OF_YEAR,1);am.setInexactRepeating(AlarmManager.RTC_WAKEUP,c.getTimeInMillis(),AlarmManager.INTERVAL_DAY,pi);Toast.makeText(this,"已设置每日 "+String.format("%02d:%02d",hour,minute)+" 学习提醒",Toast.LENGTH_SHORT).show();}

    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
