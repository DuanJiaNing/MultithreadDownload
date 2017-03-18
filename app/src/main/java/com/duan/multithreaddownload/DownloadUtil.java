package com.duan.multithreaddownload;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by DuanJiaNing on 2017/3/16.
 */

public class DownloadUtil {

   //下载文件的URL地址
    private String sourcePath;
    //下载文件保存的目标路径（包括文件名）
    private String targetFilePathAndName;
    //下载所需开启的线程数，默认为10个
    private int threadNumber = 10;
    //下载开启的线程
    private DownLoadThread[] threads;
    //下载文件总大小
    private int fileSize;
    //每个线程负责下载的文件块大小
    private int partSize;
    //最大可开启的线程数
    public static final int MAX_THREAD_NUMBER = 50;
    //默认下载文件保存路径
    private final String DEFAULT_TARGET_FOLDER_PATH = "/sdcard/Download/";
    private final String A = "application/";
    private String accept = "image/*," + A + "x-shockwave-flash," + A + "xaml+xml," + A + "vnd.ms-xpsdocument,"
            + A + "x-ms-xbap," + A + "x-ms-application," + A + "vnd.ms-excel," + A + "vnd.ms-powerpoint," + A + "msworld,*/*";
    //还在下载的线程数量
    private volatile int restTask;
    private OnDownloadFinish downloadFinish;

    //下载完成后的回调
    public interface OnDownloadFinish {
        /**
         * 所有线程都结束后调用
         * @param file 下载好的文件
         */
        void onComplete(File file);
    }

    public DownloadUtil(OnDownloadFinish complete) {
        this.downloadFinish = complete;
    }

    public int getPartSize() {
        return partSize;
    }

    public int getThreadNumber() {
        return threadNumber;
    }

    public int getFileSize() {
        return fileSize;
    }

    public String getTargetFilePathAndName() {
        return targetFilePathAndName;
    }

    /**
     * 获得目前所有线程的下载量之和
     * @return 已下载的字节数
     */
    public int getCurrentDownload() {
        int sum = 0;
        for (DownLoadThread th : threads) {
            sum += th.getCurrentDownLoaded();
        }
        return sum;
    }

    /**
     * 获得目前每个线程单独的下载量
     * @return 各个线程下载的字节数（线程名 字节数）
     */
    public String[] getCurrentThreadsDownloadCount() {
        String[] res = new String[threadNumber];
        for (int i = 0; i < threadNumber; i++) {
            res[i] = threads[i].getName() + " " + threads[i].getCurrentDownLoaded();
        }
        return res;
    }

    /**
     * 开始一次下载
     * @param sourcePath 目标URL
     * @param targetFilePath 目标保存路径
     * @param threadNumber 开启的线程数
     * @param fileName 保存的文件名
     * @throws IOException
     */
    public void start(@NonNull String sourcePath, @Nullable String targetFilePath, int threadNumber, @Nullable String fileName) throws IOException {
        this.sourcePath = sourcePath;
        this.targetFilePathAndName = targetFilePath == null ? DEFAULT_TARGET_FOLDER_PATH
                + (fileName == null ? System.currentTimeMillis() : fileName) :
                targetFilePath + (fileName == null ? System.currentTimeMillis() : fileName);
        this.threadNumber = threadNumber < 0 || threadNumber > MAX_THREAD_NUMBER ? this.threadNumber : threadNumber;
        this.restTask = this.threadNumber;
        threads = new DownLoadThread[this.threadNumber];

        HttpURLConnection conn = getConnection();
        fileSize = conn.getContentLength();
        conn.disconnect();

        RandomAccessFile file = new RandomAccessFile(targetFilePathAndName, "rw");
        file.setLength(fileSize);
        file.close();

        partSize = fileSize / threadNumber + 1;

        for (int i = 0; i < threadNumber; i++) {
            int startPos = i * partSize;
            threads[i] = new DownLoadThread(startPos);
            threads[i].start();
        }
    }


    private class DownLoadThread extends Thread {

        //当前线程的下载位置
        private int startPos;
        //当前下载保存到的目标文件（块）
        private RandomAccessFile currentPart;
        //当前已下载数据大小
        private int currentDownLoaded;

        public DownLoadThread(int startPos) throws IOException {
            this.startPos = startPos;

            currentPart = new RandomAccessFile(targetFilePathAndName, "rw");
            currentPart.seek(startPos);

        }

        public int getCurrentDownLoaded() {
            return currentDownLoaded;
        }

        @Override
        public void run() {

            try {
                HttpURLConnection connection = getConnection();
                InputStream in = connection.getInputStream();
                skipFully(in, startPos);
                byte[] bytes = new byte[1024];
                int hasRead;
                while ((currentDownLoaded < partSize) && (hasRead = in.read(bytes)) > 0) {
                    currentPart.write(bytes, 0, hasRead);
                    currentDownLoaded += hasRead;
                }
                currentPart.close();
                in.close();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                restTask--;
                if (restTask == 0)
                    downloadFinish.onComplete(new File(targetFilePathAndName));

            }

        }

    }

    private HttpURLConnection getConnection() throws IOException {
        URL url = new URL(sourcePath);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(1000 * 5);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", accept);
        conn.setRequestProperty("Accept-Language", "zh-CN");
        conn.setRequestProperty("Charset", "UTF-8");
        return conn;
    }

    /**
     * 从输入流中从起点开始跳过指定长度
     * @param in    输入流
     * @param bytes 要跳过的字节数
     * @throws IOException
     */
    public final void skipFully(InputStream in, long bytes) throws IOException {
        long len;
        while (bytes > 0) {
            len = in.skip(bytes);
            bytes -= len;
        }

    }
}
