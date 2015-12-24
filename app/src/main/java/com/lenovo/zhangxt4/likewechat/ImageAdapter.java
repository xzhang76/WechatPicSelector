package com.lenovo.zhangxt4.likewechat;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.lenovo.zhangxt4.likewechat.util.ImageLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ImageAdapter是MainActivity中GridView的适配器
 * 应该当前目录下的所有图片，以及当前目录的绝对路径
 * Created by zhangxt4 on 2015/11/29.
 */
public class ImageAdapter extends BaseAdapter {
    private String mDirPath;
    private List<String> mImgPaths;
    private LayoutInflater mInflater;
    /*
     * 用来保存被选中的图片
     * 因为在多个目录之间切换时选中图片都要保存在一起，所以要用static
     */
    private static Set<String> mSelectedImg = new HashSet<String>();

    public ImageAdapter(Context context, List<String> mDatas, String dirPath){
        this.mDirPath = dirPath;
        this.mImgPaths = mDatas;
        this.mInflater = LayoutInflater.from(context);
    }
    @Override
    public int getCount() {
        return mImgPaths.size();
    }

    @Override
    public Object getItem(int position) {
        return mImgPaths.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    //每一项
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
        if(convertView == null){
            convertView = mInflater.inflate(R.layout.item_gridview, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.mImg = (ImageView) convertView.findViewById(R.id.id_item_image);
            viewHolder.mSelect = (ImageButton) convertView.findViewById(R.id.id_item_select);
            convertView.setTag(viewHolder);
        }else{
            viewHolder = (ViewHolder) convertView.getTag();
        }
        viewHolder.mImg.setImageResource(R.mipmap.pictures_no);
        viewHolder.mSelect.setImageResource(R.mipmap.picture_unselected);
        viewHolder.mImg.setColorFilter(null);
        final String filePath = mDirPath+"/"+mImgPaths.get(position); //当前的图片路径名

        //传入当前图片的地址和对应的ImageView，调用ImageLoader的loadImage()加载图片
        ImageLoader.getInstance(3, ImageLoader.Type.LIFO).loadImage(mDirPath + "/" + mImgPaths.get(position), viewHolder.mImg);
        //为每个ImageView设置监听事件
        viewHolder.mImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSelectedImg.contains(filePath)){
                    mSelectedImg.remove(filePath);
                    viewHolder.mImg.setColorFilter(null);
                    viewHolder.mSelect.setImageResource(R.mipmap.picture_unselected);
                }else{
                    mSelectedImg.add(filePath);
                    viewHolder.mImg.setColorFilter(Color.parseColor("#77000000"));
                    viewHolder.mSelect.setImageResource(R.mipmap.pictures_selected);
                }
//                notifyDataSetChanged();   //每次点击将会闪屏，简单起见，直接在onClick()中处理
            }
        });
        if(mSelectedImg.contains(filePath)){
            viewHolder.mImg.setColorFilter(Color.parseColor("#77000000"));
            viewHolder.mSelect.setImageResource(R.mipmap.pictures_selected);
        }
        return convertView;
    }

    //ViewHolder是相对于单个显示图片而言的，所以包含item_gridview.xml中的ImageView和ImageButton
    public class ViewHolder{
        private ImageView mImg;
        private ImageButton mSelect;
    }
}
