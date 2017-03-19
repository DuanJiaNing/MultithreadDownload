package com.duan.multithreaddownload;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.Animation;
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
    //给adapter的数据
    private ArrayList<Map<String, String>> adapterDatas;
    //下载完成弹出的命名对话框
    private AlertDialog dialog;
    private EditText editText;
    //每次下载回调complete时传回的文件
    private File file;

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
        adapterDatas = new ArrayList<Map<String, String>>();
        for (int i = 0; i < downloadedInfos.size(); i++) {
            Map<String, String> map = new HashMap<>();
            map.put("name", downloadedInfos.get(i).name);
            adapterDatas.add(map);
        }
        return adapterDatas;
    }

    private void addData(@NonNull String name) {
        DownloadInfo info = new DownloadInfo(name, builder.toString());
        downloadedInfos.add(info);
        Map<String, String> map = new HashMap<>();
        map.put("name", name);
        adapterDatas.add(map); //...?
        adapter.notifyDataSetChanged();//...?如果没有定义全局的adapterDatas绑定到adapter（让adapter持有他的引用），调用该方法没有效果
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

        fileSavePath.setHint("当前默认保存路径为：/sdcard/Download/\n默认开启3个线程进行下载");

        downloadedInfos = new ArrayList<DownloadInfo>();

        adapter = new SimpleAdapter(this, getData(), R.layout.info_list_item, new String[]{"name"}, new int[]{R.id.info_it_tv});
        downloadedList.setAdapter(adapter);
        downloadedList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                taskDetail.setText(downloadedInfos.get(position).info);
            }
        });

        final AnimatorSet cvShow = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.input_show);
        final AnimatorSet cvHide = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.input_hide);
        cvShow.setTarget(cardView);
        cvHide.setTarget(cardView);

        input.setPivotX(100);//用ObjectAnimator实现旋转动画时用此方法指定旋转的原点
        final ObjectAnimator btShow = ObjectAnimator.ofFloat(input, "rotation", 0, 180).setDuration(1000);
        final ObjectAnimator btHide = ObjectAnimator.ofFloat(input, "rotation", 180, 0).setDuration(1000);
        btShow.setInterpolator(new OvershootInterpolator());
        btHide.setInterpolator(new DecelerateInterpolator());

        btShow.start();
        cvShow.start();

        input.setOnClickListener(new View.OnClickListener() {
            boolean hasCardViewShow = true;

            @Override
            public void onClick(View v) {
                if (hasCardViewShow) {
                    btHide.start();
                    cvHide.start();
                    hasCardViewShow = false;
                } else {
                    btShow.start();
                    cvShow.start();
                    hasCardViewShow = true;
                }
            }
        });
        final DownloadUtil downloadUtil = new DownloadUtil(new DownloadUtil.OnDownloadFinish() {
            @Override
            public void onComplete(File file) {
                if (timer != null) {
                    timer.cancel(); //停止"实时更新下载进度"定时器
                    timer.purge();//清除定时器
                    timer = null;
                }
                if (file == null) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "下载出错", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                //1.定义全局的MainActivity.this.file来获得file的引用
                //2.在此处定义一个final的File来获得file的引用
                //不能使用第二种方法** 当第一次下载成功回调该方法时2里的file被赋值，赋值后dialog初始化，此时dialog持有2中file的引用
                //onComplete方法执结束时2中file被回收，但dialog初始时在nclick方法里使用了他的值
                //使每次调用dialog里onclick方法时用的file都是第一次调用onComplete时的值，从而使File.reNameTo方法在调用两次之后就出错无法使用
                //而每次调用editText.setText();时用的却是该次新的final File对象使对话框能正确显示文件名
                //因此应使用全局的file对象
                MainActivity.this.file = file;
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        curDownloadSize.setText("耗时：" + new SimpleDateFormat("HH时:mm分:ss秒:SSS毫秒").format(new Date(System.currentTimeMillis() - startTime - 8 * 60 * 60 * 1000)));
                        fileSavePath.setText("文件路径：" + MainActivity.this.file.getAbsolutePath());
                        if (progressBar.getProgress() < fileSize) //文件太小时timer的时间差可能会出现下载完成了但progressBar没走完的情况
                            progressBar.setProgress(fileSize);

                        if (dialog == null) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            View viewD = getLayoutInflater().inflate(R.layout.edittext_dialog, null, false);
                            editText = (EditText) viewD.findViewById(R.id.dialog_et);
                            builder.setTitle("命名文件")
                                    .setMessage("取消则使用默认命名（用当前时间命名）")
                                    .setView(viewD)
                                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            addData(MainActivity.this.file.getName());
                                            dialog.dismiss();
                                        }
                                    })
                                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            String name = editText.getText().toString();
                                            if (name != "" && name.length() > 0) {
                                                boolean sc = MainActivity.this.file.renameTo(new File(MainActivity.this.file.getParent() + "/" + name));
                                                if (sc) {
                                                    Toast.makeText(MainActivity.this, "命名成功:" + name, Toast.LENGTH_SHORT).show();
                                                    fileSavePath.setText("文件路径：" + MainActivity.this.file.getParent() + "/" + name);
                                                    addData(name);
                                                } else {
                                                    Toast.makeText(MainActivity.this, "命名失败", Toast.LENGTH_SHORT).show();
                                                    addData(MainActivity.this.file.getName());
                                                }
                                            }
                                            dialog.dismiss();
                                        }
                                    }).setCancelable(false);
                            dialog = builder.create();
                        }
                        editText.setText(MainActivity.this.file.getName());
                        dialog.show();
                    }
                });
            }
        });

        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String pa = path.getText().toString();
                String ths = threadNumber.getText().toString();
                if (ths == "" || ths.length() == 0) //错误输入或没有输入时默认开启3个线程下载
                    ths = "3";
                int te = Integer.valueOf(ths);
                final int tn = te >= DownloadUtil.MAX_THREAD_NUMBER ? DownloadUtil.MAX_THREAD_NUMBER : te;

                if (pa == "" || pa.length() == 0) {
                    Toast.makeText(MainActivity.this, "请输入URL", Toast.LENGTH_SHORT).show();
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startTime = System.currentTimeMillis();
                            if (downloadUtil.start(pa, null, tn, null) ) { //开始下载
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
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            }
        });
    }

}
