package com.lenovo.zhangxt4.likewechat.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 图片加载类
 * Created by zhangxt4 on 2015/9/24.
 */
public class ImageLoader {
    private static ImageLoader mInstance;
    //图片缓存的核心对象，String是图片地址
    private LruCache<String, Bitmap> mLruCache;

    //线程池，负责执行任务Runnable.run()，默认线程数为1
    private ExecutorService mThreadPool;
    private static final int DEFAULT_THREAD_COUNT = 1;
    //线程池队列的调度方式
    public enum Type{
        FIFO, LIFO;
    }
    private Type mType = Type.LIFO;

    //任务队列，用来保存任务Runnable，由线程池mThreadPool来执行其中的任务
    private LinkedList<Runnable> mTaskQueue;

    //后台轮询线程，用来一直轮询（Looper）等待任务到来并执行
    private Thread mPoolThread;
    private Handler mPoolThreadHandler; //专门给后台轮询线程mPoolThread发送消息的handler（MessageQueue)，看一下其handleMessage()

    //UI线程的handler，当获取到图片地址的Bitmap之后，发送消息给UI线程更新UI
    private Handler mUIHandler;

    //用于同步mPoolThreadHandler的信号量
    private Semaphore mSemaphorePoolThreadHandler = new Semaphore(0);

    //用于同步线程池的信号量，保证线程池每执行当前任务之后再取任务执行
    private Semaphore mSemaphoreThreadPool;

    /*
     * 私有化ImageLoader，传入线程数和调度方式
     * 外面不能new一个ImageLoader实例
     */
    private ImageLoader(int threadCount, Type type){
        init(threadCount, type);
    }

    /**
     * 初始化变量
     * @param threadCount
     * @param type
     */
    private void init(int threadCount, Type type) {
        //1.后台轮询线程(Handler+Looper+MessageQueue模型）
        mPoolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        /**
                         * 当找不到图片时，就会添加一个任务到TaskQueue，然后发送消息到这里来
                         * 这里收到后会由线程池取出任务并执行
                         */
                        mThreadPool.execute(getTask());
                        try {
                            //超出线程数限度就会被阻塞住
                            mSemaphoreThreadPool.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                mSemaphorePoolThreadHandler.release(); //mPoolThreadHandler创建完毕之后释放信号量
                Looper.loop(); //loop()一直轮询取出handler的消息
            }
        };
        mPoolThread.start();

        //2.LruCache初始化
        int maxMemory = (int) Runtime.getRuntime().maxMemory();//获取应用最大可用内存
        int cacheMemory = maxMemory / 8;
        mLruCache = new LruCache<String, Bitmap>(cacheMemory) {
            @Override
            //测量每个Bitmap的总字节数
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };

        //3.初始化线程池
        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mTaskQueue = new LinkedList<Runnable>();
        mType = type;

        //4.初始化用于同步线程池的信号量
        mSemaphoreThreadPool = new Semaphore(threadCount);
    }

    /*
     * 创建单例的ImageLoader对象，注意使用了两层判断
     */
    public static ImageLoader getInstance(int threadCount, Type type){
        if(mInstance == null){
            synchronized (ImageLoader.class){
                if(mInstance == null)
                    mInstance = new ImageLoader(threadCount, type);
            }
        }
        return mInstance;
    }

    /**
     * 根据path来加载图片，并设置给imageView
     * 运行在UI线程，所以可以通过mUIHandler为ImageView设置bitMap
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView){
        imageView.setTag(path); //ImageView通过tag和path对应
        if(mUIHandler == null){
            mUIHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    //获取到图片的Bitmap，然后为imageView回调设置图片
                    ImageBeanHolder holder = (ImageBeanHolder) msg.obj;
                    Bitmap bm = holder.bitmap;
                    ImageView imageView = holder.imageView;
                    String path = holder.path;

                    //imageView和path要对应
                    if(imageView.getTag().toString().equals(path)){
                        imageView.setImageBitmap(bm);
                    }
                }
            };
        }
        //根据path在缓存中查找对应的bitmap，找不到就会添加对应的任务runnable到TaskQueue中，然后线程池调度执行
        Bitmap bm = getBitmapFromLruCache(path);
        if(bm != null){
            //bitmap已经添加到cache中，直接通过发送message给UIThread来更新UI
            Message msg = Message.obtain();
            ImageBeanHolder holder = new ImageBeanHolder();
            //bitmap、imageView、path三者对应
            holder.bitmap = bm;
            holder.imageView = imageView;
            holder.path = path;
            msg.obj = holder;
            mUIHandler.sendMessage(msg);
        }else{
            /*
             * 这里的Runnable定义了每个任务需要完成的操作：
             * 根据path获取并压缩bitmap，然后通过UIHandler来更新UI
             */
            addTasks(new Runnable() {
                @Override
                public void run() {
                    //加载图片
                    //1.获取图片需要显示的大小，即imageView的宽和高
                    ImageSize imageSize = getImageViewSize(imageView);
                    //2.根据上面获得的压缩大小压缩图片
                    Bitmap bm = decodeSampledBitmapFromPath(path, imageSize.width, imageSize.height);
                    //3.将压缩后的图片添加到cache中
                    addBitmapToLruCache(path, bm);
                    //4.sendMessage()给UI线程来设置imageView
                    Message msg = Message.obtain();
                    ImageBeanHolder holder = new ImageBeanHolder();
                    //将获取到的图片bitmap封装到message中回传给UIHandler
                    holder.bitmap = bm;
                    holder.imageView = imageView;
                    holder.path = path;
                    msg.obj = holder;
                    mUIHandler.sendMessage(msg);
                    //执行完当前的任务才释放信号量
                    mSemaphoreThreadPool.release();
                }
            });
        }
    }

    /**
     * 将压缩后的图片保存到cache中（path和bitmap是对应的）
     * @param path
     * @param bitmap
     */
    private void addBitmapToLruCache(String path, Bitmap bitmap) {
        if(getBitmapFromLruCache(path) == null){
            if(bitmap != null)
                mLruCache.put(path, bitmap);
        }
    }

    /**
     * 根据图片需要显示的宽高对图片进行压缩，返回压缩后的bitmap
     * @param path
     * @param width
     * @param height
     * @return
     */
    private Bitmap decodeSampledBitmapFromPath(String path, int width, int height) {
        //1.只是获取图片实际的宽高，并不把图片加载到内存中
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options); //options获取到图片实际的宽和高

        //2.根据实际的宽高和压缩的宽高作比较，获取一个比例
        options.inSampleSize = calculateInSampleSize(options, width, height);

        //3.获取获取的压缩比例再次解析图片
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        return bitmap;
    }

    /**
     * 根据实际宽高和需要压缩的宽高计算压缩比例——SampleSize
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;

        if(width > reqWidth || height > reqHeight){
            //需要压缩
            int widthRadio = Math.round(width*1.0f/reqWidth);
            int heightRadio = Math.round(height*1.0f/reqHeight);
            inSampleSize = Math.max(widthRadio, heightRadio);
        }
        return inSampleSize;
    }

    /**
     * 添加任务到mTaskQueue中，然后发送消息给mPoolThreadHandler
     * @param runnable
     */
    private synchronized void addTasks(Runnable runnable) {
        mTaskQueue.add(runnable);
        try {
            if (mPoolThreadHandler == null)
                //阻塞等待mPoolThreadHanlder创建完毕
                mSemaphorePoolThreadHandler.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    /**
     * 根据调度方式从任务队列中取出任务
     * @return
     */
     private Runnable getTask() {
        //根据调度方式
        if(mType == Type.FIFO){
            return mTaskQueue.removeFirst();
        }else if(mType == Type.LIFO){
            return mTaskQueue.removeLast();
        }
        return null;
    }

    /**
     * 根据imageView获取适当的压缩的宽和高
     * @param imageView
     * @return
     */
    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics(); //屏幕的尺寸
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();

        int width = imageView.getWidth(); //获取imageView的实际宽度
        if(width <= 0){
            //imageView刚new出来还没有添加到ViewGroup中
            width = lp.width; //获取imageView在layout声明的宽度
        }
        if(width <= 0){
            width = imageView.getMaxWidth(); //检查最大值
        }
        if(width <= 0){
            width = displayMetrics.widthPixels; //屏幕的宽度
        }

        int height = imageView.getHeight();
        if(height <= 0){
            height = lp.height;
        }
        if(height <= 0){
            height = imageView.getMaxHeight();
        }
        if(height <= 0){
            height = displayMetrics.heightPixels;
        }
        imageSize.width = width;
        imageSize.height = height;
        return imageSize;
    }

    /*
     * 封装imageView的宽高
     */
    private class ImageSize{
        int width;
        int height;
    }

    /**
     * 通过反射获取imageView的某个属性值
     * @param object
     * @param fieldName
     * @return
     */
    private static int getImageViewFieldValue(Object object, String fieldName){
        int value = 0;
        try {
        Field field = ImageView.class.getDeclaredField(fieldName);
        field.setAccessible(true);


            int fieldValue = field.getInt(object);
            if(fieldValue > 0 && fieldValue < Integer.MAX_VALUE){
                value = fieldValue;
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return value;
    }
    /**
     * 根据path在cache中查找bitmap
     * @param path
     * @return
     */
    private Bitmap getBitmapFromLruCache(String path) {
        return mLruCache.get(path);
    }

    //封装bitmap的类，为了避免path、imageView、bitmap之间的错乱
    private class ImageBeanHolder{
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
}
