package com.lenovo.zhangxt4.likewechat;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.lenovo.zhangxt4.likewechat.bean.FolderBean;
import com.lenovo.zhangxt4.likewechat.util.ImageLoader;

import java.util.List;

/**
 * Created by zhangxt4 on 2015/10/9.
 */
public class ListImageDirPopupWindow extends PopupWindow {
    //popup window的宽高
    private int mWidth;
    private int mHeight;
    private View mConvertView; //将popupwindow布局inflate成View
    private ListView mListView;
    private List<FolderBean> mDatas;   //ListView的数据源集合

    public interface OnDirSelectedListener{
        //mListView的接口回调，参数中包含了当前图片目录
        void onSelected(FolderBean folderBean);
    }
    public OnDirSelectedListener mListener;

    public void setOnDirSelectedListener(OnDirSelectedListener listener){
        this.mListener = listener;
    }

    //构造函数，传入的是上下文context和FolderBead的数据源
    public ListImageDirPopupWindow(Context context, List<FolderBean> datas){
        //计算popup window的宽高，通过context下下文就能得到
        calWidthAndHeight(context);
        mConvertView = LayoutInflater.from(context).inflate(R.layout.popup_main, null); //将popupwindow的布局inflate成View
        mDatas = datas;
        setContentView(mConvertView);
        setWidth(mWidth);
        setHeight(mHeight);

        setFocusable(true);
        setTouchable(true);
        setOutsideTouchable(true); //可以点击popupWindow的其他区域
        setBackgroundDrawable(new BitmapDrawable()); //保证点击外部可以消失

        //点击外部区域的效果
        setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    //如果点击外部，就消失
                    dismiss();
                    return true;
                }
                return false;
            }
        });
        initViews(context); //初始化popup window内部的控件（listView）
        initEvent();
    }

    /**
     * 为listView设置单项点击的监听事件
     * 当点击时，就会调用mListener的onSelected()方法，这个方法在MainActivity中实现
     * 这样MainActivity就知道是哪个FolderBean被点击了
     */
    private void initEvent() {
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mListener != null){
                    mListener.onSelected(mDatas.get(position));
                }
            }
        });
    }

    /**
     * 为popup window内部的listView初始化
     * @param context
     */
    private void initViews(Context context) {
        mListView = (ListView) mConvertView.findViewById(R.id.id_list_dir);
        mListView.setAdapter(new ListDirAdapter(context, mDatas));
    }

    /**
     * 计算popupwindow的宽度和高度
     * @param context, 通过上下文就能得到
     */
    private void calWidthAndHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(outMetrics);

        mWidth = outMetrics.widthPixels;
        mHeight = (int) (outMetrics.heightPixels * 0.7); //高度为屏幕高度的0.7
    }

    //popup window内部的listView的adapter类
    private class ListDirAdapter extends ArrayAdapter<FolderBean>{
        private LayoutInflater mFlater;
        private List<FolderBean> mDatas; //listView的数据源

        public ListDirAdapter(Context context, List<FolderBean> objects) {
            super(context, 0, objects);
            mFlater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null){
                holder = new ViewHolder();
                convertView = mFlater.inflate(R.layout.item_popup_main, parent, false);
                holder.mImg = (ImageView) convertView.findViewById(R.id.id_id_dir_item_image);
                holder.mDirName = (TextView) convertView.findViewById(R.id.id_dir_item_name);
                holder.mDirCount = (TextView) convertView.findViewById(R.id.id_dir_item_count);
                convertView.setTag(holder);
            }else {
                holder = (ViewHolder) convertView.getTag();
            }
            FolderBean bean = getItem(position); //得到当前位置的FolderBean
            holder.mImg.setImageResource(R.mipmap.pictures_no); //初始设置

            //为当前item的三个控件设置内容
            //loadImage()只需要传入当前文件夹第一张图片，和当前的imageView
            ImageLoader.getInstance(3, ImageLoader.Type.LIFO).loadImage(bean.getFirstImgPath(), holder.mImg);
            holder.mDirName.setText(bean.getName());
            holder.mDirCount.setText(bean.getCount() + "");
            return convertView;
        }
        //保存listView单项的viewHolder
        private class ViewHolder{
            ImageView mImg;
            TextView mDirName;
            TextView mDirCount;
        }
    }
}
