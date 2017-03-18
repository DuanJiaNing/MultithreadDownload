package com.duan.multithreaddownload;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by DuanJiaNing on 2017/3/16.
 */

public class MainActivity extends Activity {

    //当前下载文件大小
    private int fileSize;
    //进度条控件
    private ProgressBar progressBar;
    //定时刷新进度条和下载进度
    private Timer timer;
    //显示当前已下载的文件大小
    private TextView curDownloadSize;
    //任务详情
    private TextView taskDetail;
    //显示实时下载速度
    private TextView actualDownloadSpeed;
    //显示文件保存路径
    private TextView fileSavePath;
    //记录当前任务开始时间
    private long startTime;
    //显示所有已下载任务列表
    private ListView downloadedList;
    //显示和隐藏输入面板
    private FloatingActionButton input;
    //保存已下载文件信息
    private ArrayList<DownloadInfo> downloadedInfos;
    //构造“任务详情”
    private StringBuilder builder;
    //已下载列表的adapter
    private SimpleAdapter adapter;

    private class DownloadInfo {
        String name;
        String info;

        public DownloadInfo(String name, String info) {
            this.name = name;
            this.info = info;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        init();

    }

    public List<? extends Map<String, ?>> getData() {
        ArrayList<Map<String, String>> list = new ArrayList<>();
        for (int i = 0; i < downloadedInfos.size(); i++) {
            Map<String, String> map = new HashMap<>();
            map.put("name", downloadedInfos.get(i).name);
            list.add(map);
        }
        return list;
    }

    private void addData(@NonNull String name) {
        DownloadInfo info = new DownloadInfo(name, builder.toString());
        downloadedInfos.add(info);
        adapter.notifyDataSetChanged();
    }

    private void init() {
        downloadedList = (ListView) findViewById(R.id.main_lv);
        input = (FloatingActionButton) findViewById(R.id.main_fbt);
        curDownloadSize = (TextView) findViewById(R.id.main_tv);
        taskDetail = (TextView) findViewById(R.id.main_tv_);
        progressBar = (ProgressBar) findViewById(R.id.main_pb);
        actualDownloadSpeed = (TextView) findViewById(R.id.main_tv_speed);
        fileSavePath = (TextView) findViewById(R.id.main_tv_path);
        final EditText path = (EditText) findViewById(R.id.main_et);
        final EditText threadNumber = (EditText) findViewById(R.id.main_et_thread_num);
        Button download = (Button) findViewById(R.id.main_bt);
        final View cardView = findViewById(R.id.main_cv);

        fileSavePath.setHint("当前默认保存路径为：/sdcard/Download/\n默认开启5个线程进行下载");

        downloadedInfos = new ArrayList<DownloadInfo>();

        adapter = new SimpleAdapter(this, getData(), R.layout.info_list_item, new String[]{"name"}, new int[]{R.id.info_it_tv});
        downloadedList.setAdapter(adapter);
        downloadedList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                taskDetail.setText(downloadedInfos.get(position).info);
            }
        });

        final AnimationSet cvShow = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.input_show);
        final AnimationSet cvHide = (AnimationSet) AnimationUtils.loadAnimation(this, R.anim.input_hide);
        cvShow.setFillAfter(true);
        cvHide.setFillAfter(true);

        input.setPivotX(100);//用ObjectAnimator实现旋转动画时用此方法指定旋转的原点
        final ObjectAnimator btShow = ObjectAnimator.ofFloat(input, "rotation", 0, 180).setDuration(1000);
        final ObjectAnimator btHide = ObjectAnimator.ofFloat(input, "rotation", 180, 0).setDuration(1000);
        btShow.setInterpolator(new OvershootInterpolator());
        btHide.setInterpolator(new DecelerateInterpolator());

        input.setOnClickListener(new View.OnClickListener() {
            boolean hasCardViewShow = false;

            @Override
            public void onClick(View v) {
                if (hasCardViewShow) {
                    btHide.start();
                    cardView.startAnimation(cvHide);
                    hasCardViewShow = false;
                } else {
                    btShow.start();
                    cardView.startAnimation(cvShow);
                    hasCardViewShow = true;
                }
            }
        });

        final DownloadUtil downloadUtil = new DownloadUtil(new DownloadUtil.OnDownloadComplete() {
            @Override
            public void oncomplete(File file) {
                final File fil = file;
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        timer.cancel(); //停止"实时更新下载进度"定时器
                        curDownloadSize.setText("耗时：" + new SimpleDateFormat("HH时:mm分:ss秒:SSS毫秒").format(new Date(System.currentTimeMillis() - startTime - 8 * 60 * 60 * 1000)));
                        fileSavePath.setText("文件路径：" + fil.getAbsolutePath());
                        if (progressBar.getProgress() < fileSize) //文件太小时timer的时间差可能会出现下载完成了但progressBar没走完的情况
                            progressBar.setProgress(fileSize);

                        final View viewD = getLayoutInflater().inflate(R.layout.edittext_dialog, null, false);
                        final EditText edit = (EditText) viewD.findViewById(R.id.dialog_et);
                        edit.setText(fil.getName());
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("命名文件")
                                .setMessage("取消则使用默认命名（用当前时间命名）")
                                .setView(viewD)
                                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        addData(fil.getName());
                                        dialog.dismiss();
                                    }
                                })
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String name = edit.getText().toString();
                                        if (name != "" && name.length() > 0) {
                                            if (fil.renameTo(new File(fil.getParent() + "/" + name))) {
                                                Toast.makeText(MainActivity.this, "命名成功:" + name, Toast.LENGTH_SHORT).show();
                                                fileSavePath.setText("文件路径：" + fil.getParent() + "/" + name);
                                                addData(name);
                                            } else {
                                                Toast.makeText(MainActivity.this, "命名失败", Toast.LENGTH_SHORT).show();
                                                addData(fil.getName());
                                            }
                                        }
                                        dialog.dismiss();
                                    }
                                }).setCancelable(false).show();
                    }
                });
            }
        });

        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String pa = path.getText().toString();
                String ths = threadNumber.getText().toString();
                if (ths == "" || ths.length() == 0) //错误输入或没有输入时默认开启5个线程下载
                    ths = "5";
                int te = Integer.valueOf(ths);
                final int tn = te >= 100 ? 100 : te;

                if (pa == "" || pa.length() == 0) {
                    Toast.makeText(MainActivity.this, "请输入URL", Toast.LENGTH_SHORT).show();
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startTime = System.currentTimeMillis();
                            downloadUtil.start(pa, null, tn, null); //开始下载
                            fileSize = downloadUtil.getFileSize();
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setMax(fileSize);
                                    progressBar.setProgress(0);
                                    String mb = new DecimalFormat("0.00").format(downloadUtil.getFileSize() / Math.pow(2, 20));
                                    if (builder == null)
                                        builder = new StringBuilder();
                                    else builder.delete(0, builder.length());
                                    builder.append("任务信息：\n")
                                            .append("URL:")
                                            .append(pa)
                                            .append("\n下载文件大小：")
                                            .append(downloadUtil.getFileSize() + "字节(" + (downloadUtil.getFileSize() >> 10) + "KB)" + " (" + mb + "MB)")
                                            .append("\n实际下载线程数：")
                                            .append(downloadUtil.getThreadNumber())
                                            .append("\n每个线程负责下载：")
                                            .append(downloadUtil.getPartSize() + "字节")
                                            .append("(" + (downloadUtil.getPartSize() >> 10) + "KB)" + "\n");
                                    taskDetail.setText(builder.toString());
                                }
                            });

                            if (timer == null)
                                timer = new Timer();
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    MainActivity.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            int cu = downloadUtil.getCurrentDownload();
                                            progressBar.setProgress(cu);
                                            curDownloadSize.setText(cu + "/" + fileSize + "(字节)\n" + (cu >> 10) + "/" + (fileSize >> 10) + "(kb)");
                                            Calendar ca = Calendar.getInstance();
                                            ca.setTimeInMillis(System.currentTimeMillis() - startTime);
                                            int seco = ca.get(Calendar.SECOND);
                                            int se = cu / (seco == 0 ? 1 : seco);
                                            actualDownloadSpeed.setText("下载速度：" + se + " byte/s \n" + (se >> 10) + " kb/s"); //当下载文件太小时会引发除0错误
                                        }
                                    });
                                }
                            }, 0, 100); //没100ms更新一次下载进度和速度
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).start();

            }
        });
    }

}