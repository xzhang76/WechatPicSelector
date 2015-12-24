package com.lenovo.zhangxt4.likewechat.bean;

/**
 * Created by zhangxt4 on 2015/10/8.
 */
public class FolderBean {
    private String dir; //当前文件夹的路径
    private String firstImgPath; //当前文件夹中第一个图片地址
    private String name; //当前文件夹的名字
    private int count; //当前文件夹中图片的数量

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
        int lastIndexOf = this.dir.lastIndexOf("/") + 1;
        this.name = this.dir.substring(lastIndexOf);   //当前文件夹的名字
    }

    public String getFirstImgPath() {
        return firstImgPath;
    }

    public void setFirstImgPath(String firstImgPath) {
        this.firstImgPath = firstImgPath;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
