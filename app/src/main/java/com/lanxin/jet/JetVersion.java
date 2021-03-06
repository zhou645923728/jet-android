package com.lanxin.jet;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JetVersion {

    private JetResource jetResource;

    //文件下载任务线程池（并行3组线程）
    protected ExecutorService executorService = Executors.newFixedThreadPool(3);

    public JetVersion(JetResource jetResource) {
        this.jetResource = jetResource;
        File cachePath = new File(jetResource.cachePath);
        if (! cachePath.exists()) {
            cachePath.mkdirs();
        }
    }

    /**
     * 同步线上版本
     */
    public void syncVersion() {
        @SuppressLint("HandlerLeak") final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String result = msg.obj.toString();
                if (! result.equals("")) {
                    boolean changeConfig = false;
                    try {
                        //获取本地版本同步列表
                        JSONObject localList = null;
                        if (jetResource.localConfig != null) {
                            localList = jetResource.localConfig.getJSONObject(jetResource.SYNC_LIST);
                        } else {
                            Log.i("====>", "没有配置文件");
                        }
                        //获取线上版本同步列表
                        JSONObject onlineConfig = new JSONObject(result);
                        JSONObject syncList = onlineConfig.getJSONObject(jetResource.SYNC_LIST);
                        Iterator syncListKeys = syncList.keys();
                        while (syncListKeys.hasNext()) {
                            String file = syncListKeys.next().toString();
                            int newVersion = syncList.getInt(file);
                            int oldVersion = localList != null && !localList.isNull(file)
                                    ? localList.getInt(file) : 0;
                            if (newVersion > oldVersion) {
                                changeConfig = true;
                                syncFile(file);
                            }
                        }
                        //更新本地版本库
                        if (changeConfig) {
                            File file = new File(jetResource.cachePath + jetResource.CONFIG_NAME + jetResource.configVersion + ".json");
                            file.createNewFile();
                            FileOutputStream os = new FileOutputStream(file);
                            os.write(result.getBytes("UTF-8"));
                            os.close();
                        }
                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder stringBuilder = new StringBuilder();
                try {
                    URL url = new URL(jetResource.serverHost + "getConfig/" + jetResource.configVersion);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                        BufferedReader buffer = new BufferedReader(reader);
                        String read;
                        while ((read = buffer.readLine()) != null) {
                            stringBuilder.append(read);
                        }
                        buffer.close();
                        reader.close();
                    }
                    connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Message message = handler.obtainMessage(0, stringBuilder.toString());
                handler.sendMessage(message);
            }
        }).start();
    }

    /**
     * 同步线上文件
     * @param fileName
     */
    private void syncFile(final String fileName) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(jetResource.serverHost + "getFile/" + fileName);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                        BufferedReader buffer = new BufferedReader(reader);
                        StringBuilder stringBuilder = new StringBuilder();
                        String read;
                        while ((read = buffer.readLine()) != null) {
                            stringBuilder.append(read);
                        }
                        reader.close();
                        //文件内容写入sd卡
                        File file = new File(jetResource.cachePath + fileName);
                        if (! file.exists()) {
                            file.createNewFile();
                        }
                        FileOutputStream os = new FileOutputStream(file);
                        os.write(stringBuilder.toString().getBytes("UTF-8"));
                        os.close();
                        buffer.close();
                        reader.close();
                        Log.i("---->", "文件下载成功："+fileName);
                    }
                    connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
