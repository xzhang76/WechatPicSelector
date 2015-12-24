package com.lenovo.zhangxt4.likewechat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.lenovo.zhangxt4.likewechat.bean.FolderBean;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private GridView mGridView;
    private List<String> mImgs; //GridView的数据集合
    private ImageAdapter mImageAdapter; //GridView的adapter

    private RelativeLayout mBottomLy;
    private TextView mDirName;
    private TextView mDirCount;

    private File mCurrentDir;
    private int mMaxCount;

    //从底部弹上来的popup window的数据集，没一项都是一个FolderBean
    private List<FolderBean> mFolderBeans = new ArrayList<FolderBean>();

    private ProgressDialog mProgressDialog;

    private ListImageDirPopupWindow mDirPopupWindow;

    private static final int DATA_LOADED = 0x110;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initDatas();
        initEvent();
    }

    //扫描完所有的图片之后就会sendMessage到这里
    private android.os.Handler mHandler = new android.os.Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == DATA_LOADED){
                mProgressDialog.dismiss();
                data2View(); //绑定数据源到GridView中
                initDirPopupWindow(); //初始化popupWindow，加载数据源到ListView中
            }
        }
    };

    private void initDirPopupWindow() {
        mDirPopupWindow = new ListImageDirPopupWindow(this, mFolderBeans);
        //设置popup window消失时外部变亮的效果
        mDirPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                lightOn();
            }
        });
        //为popup window设置选择FolderBean的选中回调
        mDirPopupWindow.setOnDirSelectedListener(new ListImageDirPopupWindow.OnDirSelectedListener() {
            @Override
            public void onSelected(FolderBean folderBean) {
                //1.重新获取当前被选择的目录和数据源，用它们来创建GridView的adapter
                mCurrentDir = new File(folderBean.getDir());
                mImgs = Arrays.asList(mCurrentDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png"))
                            return true;
                        return false;
                    }
                }));
                //2.用重新获取的目录名和数据源创建GridView的adapter
                mImageAdapter = new ImageAdapter(MainActivity.this, mImgs, mCurrentDir.getAbsolutePath());
                mGridView.setAdapter(mImageAdapter);
                //3.重新更新底部布局的控件
                mDirName.setText(folderBean.getName());
                mDirCount.setText(folderBean.getCount() + "");
                mDirPopupWindow.dismiss(); //popup window要消失
            }
        });
    }

    /**
     * 内容区域变亮
     * 小变动：只有gridView部分才会变亮
     */
    private void lightOn() {
//        WindowManager.LayoutParams lp = getWindow().getAttributes();  //得到window的布局参数
//        lp.alpha = 1.0f;
//        getWindow().setAttributes(lp);
        mGridView.setAlpha(1.0f);
    }

    /**
     * 内容区域变暗
     */
    private void lightOff() {
//        WindowManager.LayoutParams lp = getWindow().getAttributes();  //得到window的布局参数
//        lp.alpha = 0.3f;
//        getWindow().setAttributes(lp);
        mGridView.setAlpha(0.3f);
    }

    /**
     * 添加数据源到view中
     */
    private void data2View() {
        if (mCurrentDir == null){
            Toast.makeText(this, "未扫描到图片", Toast.LENGTH_SHORT).show();
            return;
        }
        mImgs = Arrays.asList(mCurrentDir.list()); //得到当前目录下所有的图片名的集合(不带前面的路径)
        mImageAdapter = new ImageAdapter(this, mImgs, mCurrentDir.getAbsolutePath()); //GridView对应的adapter
        mGridView.setAdapter(mImageAdapter);

        mDirCount.setText(mMaxCount + "");
        mDirName.setText(mCurrentDir.getName());
    }

    /**
     * 相应的点击事件：
     * 点击底部布局时弹出popup window
     */
    private void initEvent() {
        mBottomLy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDirPopupWindow.setAnimationStyle(R.style.dir_popupwindow_anim); //从下往上出现，从上往下消失
                mDirPopupWindow.showAsDropDown(mBottomLy, 0, 0); //设置显示位置为底部
                lightOff();
            }
        });
    }

    /**
     * 开启一个单独的线程，利用ContentProvider去遍历存储卡中的所有图片
     * 然后通过handler通知主线程去更新initView()初始化过的控件
     */
    private void initDatas() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "当前存储卡不可用！", Toast.LENGTH_SHORT).show();
            return;
        }
        mProgressDialog = ProgressDialog.show(this, null, "正在加载...");

        new Thread() {
            //扫描手机中的图片
            @Override
            public void run() {
                Uri mImgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;  //uri指向手机中存储的所有图片
                ContentResolver cr = MainActivity.this.getContentResolver(); //使用ContentResolver
                Cursor cursor = cr.query(mImgUri, null,
                        MediaStore.Images.Media.MIME_TYPE + " = ? or "
                                + MediaStore.Images.Media.MIME_TYPE + " = ? ", new String[]{"image/jpeg", "image/png"},
                        MediaStore.Images.Media.DATE_MODIFIED);
                //遍历cursor获取每个图片，通过图片的位置获取所在的文件夹
                Set<String> mDirPaths = new HashSet<String>(); //用于存储遍历过的图片目录路径
                while(cursor.moveToNext()){
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)); //获取当前图片的路径
                    File parentFile = new File(path).getParentFile(); //获取当前图片所在的目录
                    if(parentFile == null){
                        continue;
                    }
                    String dirPath = parentFile.getAbsolutePath(); //当前图片所在目录字符串

                    FolderBean folderBean = null;
                    if(mDirPaths.contains(dirPath)){
                        continue; //当前路径已经扫描过了
                    }else {
                        //当前的图片目录是第一次出现
                        mDirPaths.add(dirPath);
                        folderBean = new FolderBean();
                        folderBean.setDir(dirPath);
                        folderBean.setFirstImgPath(path);
                    }
                    if(parentFile.list() == null){
                        continue;
                    }
                    //获取当前目录下图片的张数（过滤出图片）
                    int picSize = parentFile.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            if(filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png"))
                                return true;
                            return false;
                        }
                    }).length;
                    folderBean.setCount(picSize);

                    mFolderBeans.add(folderBean); //popup window的数据源集合

                    //这里是为了GridView和popup window中默认显示的就是图片数量最多的目录
                    if (picSize > mMaxCount){
                        mMaxCount = picSize;
                        mCurrentDir = parentFile;
                    }
                }
                cursor.close();
                //通知UI线程，扫描已经完成
                mHandler.sendEmptyMessage(DATA_LOADED);
            }
        }.start();

    }

    /**
     * 初始activity_main中的控件
     */
    private void initView() {
        mGridView = (GridView) findViewById(R.id.id_gridView);
        mBottomLy = (RelativeLayout) findViewById(R.id.id_bottom_ly);
        mDirName = (TextView) findViewById(R.id.id_dir_name);
        mDirCount = (TextView) findViewById(R.id.id_dir_count);

    }
}
