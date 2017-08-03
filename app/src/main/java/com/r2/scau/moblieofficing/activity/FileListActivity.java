package com.r2.scau.moblieofficing.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.r2.scau.moblieofficing.R;
import com.r2.scau.moblieofficing.adapter.FileManagerAdapter;
import com.r2.scau.moblieofficing.bean.FileBean;
import com.r2.scau.moblieofficing.bean.GsonFileJsonBean;
import com.r2.scau.moblieofficing.untils.Contacts;
import com.r2.scau.moblieofficing.untils.DateUtils;
import com.r2.scau.moblieofficing.untils.OkHttpClientManager;
import com.r2.scau.moblieofficing.untils.ToastUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

import static com.r2.scau.moblieofficing.untils.OkHttpClientManager.okHttpClient;

/**
 * Created by EdwinCheng on 2017/7/24.
 *
 */

public class FileListActivity extends BaseActivity {

    private static final String TAG = "FileListActivity";
    private RecyclerView fileListRecycler;
    private LinearLayoutManager linearLayoutManager;
    private FileManagerAdapter fileManagerAdapter;
    private Button filelist_editBtn, filelist_newfolderBtn;
    private Toolbar mtoolbar;
    private TextView titleTV;

    private GsonFileJsonBean fileJson;
    private List<FileBean> fileList;
    private String rootPath;
    private Stack<String> currentPathStack;
    private long lastBackPressed = 0;
    /**
     * initState为true时候，是需要初始化的,代表你是第一次加载Adapter
     */
    private boolean initState;
    private String fileSelectType;

    private Bundle bundle;
    private Handler handler;
    private Gson gson;

    private FormBody formBody;
    private Request request;
    private Message message;

    @Override
    protected void initView() {
        setContentView(R.layout.activity_filelist);
        fileListRecycler = (RecyclerView) findViewById(R.id.filelist_recycler);
        filelist_editBtn = (Button) findViewById(R.id.filelist_editBtn);
        filelist_newfolderBtn = (Button) findViewById(R.id.filelist_newfolderBtn);
        mtoolbar = (Toolbar) findViewById(R.id.toolbar);
        titleTV = (TextView) findViewById(R.id.toolbar_title);

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case Contacts.FILEMANAGER.GETDIR_SUCCESS:
                        //初始化状态
                        if (fileManagerAdapter == null || initState == true) {
                            initRecycler();
                            initState = false;
                        } else if (initState == false) {
                            //非初始化状态
                            fileList = fileManagerAdapter.setFileList(fileList, getPathString());
                        }
                        break;
                }
            }
        };
    }

    @Override
    protected void initData() {
        mtoolbar.setTitle("");
        FileListActivity.this.setSupportActionBar(mtoolbar);
        FileListActivity.this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (fileJson == null) {
            initState = true;
            fileJson = new GsonFileJsonBean();
            fileList = new ArrayList<>();
            currentPathStack = new Stack<>();
            bundle = new Bundle();
            bundle = getIntent().getExtras();
            gson = new Gson();
            linearLayoutManager = new LinearLayoutManager(FileListActivity.this);
        }

        //判断是"个人文件" 还是公共文件
        fileSelectType = bundle.getString("intenttype");

        if (fileSelectType.equals("personalfile")){
            titleTV.setText(R.string.my_file);
        }else if (fileSelectType.equals("sharedfile")){
            titleTV.setText(R.string.shared_file);
        }

        //默认的路径
        rootPath = "/";
        /**
         * Created by EdwinCheng on 2017/7/25.
         * 将根路径push进栈
         * 并将file[]赋值给arraylist
         */
        currentPathStack.push(rootPath);
        getFileListFromServer(getPathString(),fileSelectType);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        FileListActivity.this.getMenuInflater().inflate(R.menu.toolbar_filemanage_upload_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                FileListActivity.this.finish();
                break;

            case R.id.menu_upload:
                bundle.clear();
                bundle.putString("remotePath",getPathString());
                FileListActivity.this.openActivityForResult(UploadSelectFileActivity.class,bundle,Contacts.RequestCode.UPLOAD);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initRecycler() {
        fileManagerAdapter = new FileManagerAdapter(FileListActivity.this, fileList, getPathString(), fileSelectType);
        fileListRecycler.setLayoutManager(linearLayoutManager);
        fileListRecycler.setAdapter(fileManagerAdapter);

        this.fileManagerAdapter.setOnItemClickLitener(new OnItemClickLitener() {
            @Override
            public void onItemClick(View view, int position) {
                FileBean fileBean = fileList.get(position);
                //对文件的处理
                if (fileBean.getAttribute() == Contacts.FILEMANAGER.FILE_TYPE) {
                    ToastUtils.show(FileListActivity.this, "点击的是文件" + fileBean.getName(), Toast.LENGTH_SHORT);

                    doDowmload(getPathString(),fileBean.getName());

                } else {
                    /**
                     * 如果是文件夹
                     * 清除列表数据
                     * 获得目录中的内容，计入列表中
                     * 适配器通知数据集改变
                     */
                    ToastUtils.show(FileListActivity.this, "点击的是文件夹" + fileBean.getName(), Toast.LENGTH_SHORT);
                    currentPathStack.push("/" + fileBean.getName());
                    showChange(getPathString());
                }
            }

            @Override
            public void onItemLongClick(View view, int position) {
                ToastUtils.show(FileListActivity.this, "长点击position:" + position, Toast.LENGTH_SHORT);
            }
        });
    }

    private void doDowmload(String currentDir,String filename){
        String downloadPath = currentDir + "/" + filename;
        if (fileSelectType.equals("personalfile")){
            formBody = new FormBody.Builder()
                    .add("path", downloadPath)
                    .add("userPhone", "123456789010")
                    .build();
            request = new Request.Builder().url(Contacts.computer_ip + Contacts.file_Server + Contacts.filedownload)
                    .addHeader("cookie", OkHttpClientManager.loginSessionID)
                    .post(formBody)
                    .build();
        }else {
            formBody = new FormBody.Builder()
                    .add("fileName",filename)
                    .add("groupId", "123456789010")
                    .build();
            request = new Request.Builder().url(Contacts.computer_ip + Contacts.file_Server + Contacts.downLoadGroupFile)
                    .addHeader("cookie", OkHttpClientManager.loginSessionID)
                    .post(formBody)
                    .build();
        }

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // TODO: 10-0-1  请求失败
                Log.e(TAG, "getFileListFromServer  fail");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // TODO: 10-0-1 请求成功
//                message = handler.obtainMessage();
//                if (response.code() == 200){
//
//                    message.what = Contacts.FILEMANAGER.down;
//                }else{
//                    Log.e(TAG, "网络请求 错误  "+ response.code() + "   " + response.message() );
//                    message.what = Contacts.FILEMANAGER.GETDIR_FAILURE;
//                }
//                handler.sendMessage(message);
                Log.e(TAG, "onResponse: download::" + response.body().string() );
            }
        });

    }

    @Override
    protected void initListener() {
        filelist_editBtn.setOnClickListener(FileListActivity.this);
        filelist_newfolderBtn.setOnClickListener(FileListActivity.this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.filelist_editBtn:
                break;
            case R.id.filelist_newfolderBtn:
                bundle.clear();
                bundle.putString("renameType", "newfolder");
                /**
                 * 应该还要传输当前的路径 ？
                 * Create by edwincheng in 2027/7/27.
                 */
                bundle.putString("path", getPathString());
                openActivityForResult(FMRenameActivity.class , bundle , Contacts.RequestCode.CREATE);
                break;
        }
    }

    //显示改变data之后的文件数据列表
    //先清楚filelist，再发起网络请求，获取文件网盘目录
    private void showChange(String path) {
        fileList.clear();
        getFileListFromServer(path,fileSelectType);
    }

    //显示当前路径
    private String getPathString() {
        Stack<String> temp = new Stack<>();
        temp.addAll(currentPathStack);
        String result = "";
        while (temp.size() != 0) {
            result = temp.pop() + result;
        }
        return result;
    }

    @Override
    public void onBackPressed() {
        if (currentPathStack.peek() == rootPath) {
            FileListActivity.this.finish();
        } else {
            currentPathStack.pop();
            showChange(getPathString());
        }
    }

    /**
     * Create by edwincheng in 2017/7/28.
     *
     * 一个可以将文件Json转化成Object的方法
     */
    public static ArrayList<FileBean> fileJsonToObject(GsonFileJsonBean tempJsonBean) {
        ArrayList<FileBean> tempFileList = new ArrayList<>();
        for (String str : tempJsonBean.getFiles()) {
            String[] fi = str.split(";");
            Log.e(TAG, "fileJsonToObject 文件" + fi.length);
            tempFileList.add(new FileBean(fi[0], Integer.parseInt(fi[1]), DateUtils.timete(fi[2]), Contacts.FILEMANAGER.FILE_TYPE));
        }
        for (String str : tempJsonBean.getFolders()) {
            String[] fo = str.split(";");
            Log.e(TAG, "fileJsonToObject 文件夹" + fo.length);
            tempFileList.add(new FileBean(fo[0], Integer.parseInt(fo[1]), DateUtils.timete(fo[2]), Contacts.FILEMANAGER.FOLDER_TYPE));
        }
        Log.e(TAG, "tempFileLists .size "+ tempFileList.size());
        return tempFileList;
    }

    private void  getFileListFromServer(String path,String fileSelectType) {
        if (fileSelectType.equals("personalfile")){
            formBody = new FormBody.Builder()
                    .add("path", path)
                    .add("userPhone", "123456789010")
                    .build();
            request = new Request.Builder().url(Contacts.computer_ip + Contacts.file_Server + Contacts.getDir)
                    .addHeader("cookie", OkHttpClientManager.loginSessionID)
                    .post(formBody)
                    .build();
        }else {
            formBody = new FormBody.Builder()
                    .add("groupId", "123456789010")
                    .build();
            request = new Request.Builder().url(Contacts.computer_ip + Contacts.file_Server + Contacts.getGroupDir)
                    .addHeader("cookie", OkHttpClientManager.loginSessionID)
                    .post(formBody)
                    .build();
        }

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // TODO: 10-0-1  请求失败
                Log.e(TAG, "getFileListFromServer  fail");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // TODO: 10-0-1 请求成功
                message = handler.obtainMessage();
                if (response.code() == 200){
                    fileJson = gson.fromJson(response.body().string(), GsonFileJsonBean.class);
                    fileList = fileJsonToObject(fileJson);
                    message.what = Contacts.FILEMANAGER.GETDIR_SUCCESS;
                }else{
                    Log.e(TAG, "网络请求 错误  "+ response.code() + "   " + response.message() );
                    message.what = Contacts.FILEMANAGER.GETDIR_FAILURE;
                }
                handler.sendMessage(message);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case Contacts.RequestCode.RENAME:
                if(resultCode == RESULT_OK){
                    ToastUtils.show(FileListActivity.this,"重命名成功", Toast.LENGTH_SHORT);
                    showChange(getPathString());
                }else if(resultCode == RESULT_CANCELED){
                    ToastUtils.show(FileListActivity.this,"用户选择取消", Toast.LENGTH_SHORT);
                }else{

                }
                break;

            case Contacts.RequestCode.CREATE:
                if(resultCode == RESULT_OK){
                    ToastUtils.show(FileListActivity.this,"新建文件成功", Toast.LENGTH_SHORT);
                    showChange(getPathString());
                }else if(resultCode == RESULT_CANCELED){
                    ToastUtils.show(FileListActivity.this,"用户选择取消", Toast.LENGTH_SHORT);
                }else{

                }
                break;

            case Contacts.RequestCode.UPLOAD:
                if (resultCode == RESULT_OK){
                    ToastUtils.show(FileListActivity.this,"上传文件成功", Toast.LENGTH_SHORT);
                    showChange(getPathString());
                }else if (resultCode == RESULT_CANCELED){
                    ToastUtils.show(FileListActivity.this,"用户选择取消", Toast.LENGTH_SHORT);
                }else{

                }
                break;

        }
    }

}
