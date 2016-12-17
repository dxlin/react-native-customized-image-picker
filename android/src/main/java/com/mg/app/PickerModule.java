package com.mg.app;

import android.app.Activity;
import android.graphics.BitmapFactory;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;

import java.io.File;
import java.util.List;

import cn.finalteam.rxgalleryfinal.RxGalleryFinal;
import cn.finalteam.rxgalleryfinal.bean.ImageCropBean;
import cn.finalteam.rxgalleryfinal.bean.MediaBean;
import cn.finalteam.rxgalleryfinal.imageloader.ImageLoaderType;
import cn.finalteam.rxgalleryfinal.rxbus.RxBusResultSubscriber;
import cn.finalteam.rxgalleryfinal.rxbus.event.ImageMultipleResultEvent;
import cn.finalteam.rxgalleryfinal.rxbus.event.ImageRadioResultEvent;

class PickerModule extends ReactContextBaseJavaModule {
    private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";

    private Promise mPickerPromise;

    private boolean cropping = false;
    private boolean multiple = false;
    private boolean isCamera = false;
    //Light Blue 500
    private int width = 200;
    private int height = 200;
    private int maxSize = 9;
    private final ReactApplicationContext mReactContext;

    PickerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mReactContext = reactContext;
    }

    @Override
    public String getName() {
        return "ImageCropPicker";
    }

    private void setConfiguration(final ReadableMap options) {
        multiple = options.hasKey("multiple") && options.getBoolean("multiple");
        isCamera = options.hasKey("isCamera") && options.getBoolean("isCamera");
        width = options.hasKey("width") ? options.getInt("width") : width;
        height = options.hasKey("height") ? options.getInt("height") : height;
        maxSize = options.hasKey("maxSize") ? options.getInt("maxSize") : maxSize;
        cropping = options.hasKey("cropping") ? options.getBoolean("cropping") : cropping;
    }

    private WritableMap getImage(String path) throws Exception {
        WritableMap image = new WritableNativeMap();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            throw new Exception("Cannot select remote files");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(path, options);

        if (options.outMimeType == null || options.outWidth == 0 || options.outHeight == 0) {
            throw new Exception("Invalid image selected");
        }

        image.putString("path", "file://" + path);
        image.putInt("width", options.outWidth);
        image.putInt("height", options.outHeight);
        image.putString("mime", options.outMimeType);
        image.putInt("size", (int) new File(path).length());

/*        if (includeBase64) {
            image.putString("data", getBase64StringFromFile(path));
        }*/

        return image;
    }

    private WritableMap getImage(ImageCropBean result) throws Exception {

        String path = result.getOriginalPath();
        return getImage(path);
    }
    private WritableMap getImage(MediaBean result) throws Exception {

        String path = result.getOriginalPath();
        return getImage(path);
    }
    private void initImageLoader(Activity activity) {

        ImageLoaderConfiguration.Builder config = new ImageLoaderConfiguration.Builder(activity);
        config.threadPriority(Thread.NORM_PRIORITY - 2);
        config.denyCacheImageMultipleSizesInMemory();
        config.diskCacheFileNameGenerator(new Md5FileNameGenerator());
        config.diskCacheSize(50 * 1024 * 1024); // 50 MiB
        config.tasksProcessingOrder(QueueProcessingType.LIFO);
        ImageLoader.getInstance().init(config.build());
    }

    @ReactMethod
    public void openPicker(final ReadableMap options, final Promise promise) {
        final Activity activity = getCurrentActivity();

        if (activity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        setConfiguration(options);
        initImageLoader(activity);
        mPickerPromise = promise;

        RxGalleryFinal rxGalleryFinal =  RxGalleryFinal.with(activity);
        if(!isCamera){
            rxGalleryFinal.hideCamera();
        }
        if(!this.multiple) {
            if(!cropping){
                rxGalleryFinal.crop();
            }
            rxGalleryFinal
                    .image()
                    .radio()
                    .imageLoader(ImageLoaderType.GLIDE)
                    .subscribe(new RxBusResultSubscriber<ImageRadioResultEvent>() {
                        @Override
                        protected void onEvent(ImageRadioResultEvent imageRadioResultEvent) throws Exception {
                            //Toast.makeText(getBaseContext(), imageRadioResultEvent.getResult().getOriginalPath(), Toast.LENGTH_SHORT).show();
                            ImageCropBean result = imageRadioResultEvent.getResult();
                            WritableArray resultArr = new WritableNativeArray();
                            resultArr.pushMap(getImage(result));
                            mPickerPromise.resolve(resultArr);
                        }
                    })
                    .openGallery();
        } else {
            rxGalleryFinal
                    .image()
                    .multiple()
                    .maxSize(maxSize)
                    .imageLoader(ImageLoaderType.GLIDE)
                    .subscribe(new RxBusResultSubscriber<ImageMultipleResultEvent>() {
                        @Override
                        protected void onEvent(ImageMultipleResultEvent imageMultipleResultEvent) throws Exception {
                            //Toast.makeText(getBaseContext(), "已选择" + imageMultipleResultEvent.getResult().size() +"张图片", Toast.LENGTH_SHORT).show();
                            List<MediaBean> list = imageMultipleResultEvent.getResult();
                            WritableArray resultArr = new WritableNativeArray();
                            for(MediaBean bean:list){
                                resultArr.pushMap(getImage(bean));
                            }
                            mPickerPromise.resolve(resultArr);

                            mPickerPromise.resolve(list);
                        }
                    })
                    .openGallery();
        }
    }
}