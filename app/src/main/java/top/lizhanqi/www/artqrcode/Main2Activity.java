package top.lizhanqi.www.artqrcode;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import pl.droidsonroids.gif.GifDrawable;
import top.lizhanqi.www.artcore.AnimatedGifEncoder;
import top.lizhanqi.www.artcore.ArtQRCode;
import top.lizhanqi.www.artcore.ArtQRCodeUtil;
import top.lizhanqi.www.cropperlib.CropImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 生成的核心方法:
 * //图像模式,即选择图片生成为二维码
 * mQRBitmap = ArtQRCode.Product(qrText, mCropImage, colorful, color);
 * logo
 * mQRBitmap = ArtQRCode.ProductLogo(mCropImage, qrText, colorful, color);
 * 嵌入在某一个图片中
 * mQRBitmap = ArtQRCode.ProductEmbed(qrText, mCropImage, colorful, color, mCropSize.x, mCropSize.y, mOriginBitmap);
 * Gif
 * ArtQRCode.ProductGIF(qrText, gifArray, colorful, color);
 */
public class Main2Activity extends Activity {
    String qrText = "我是素玄06,工号sx-06";
    private Bitmap mOriginBitmap;//原版图
    private Bitmap mQRBitmap;//二维码图
    private File shareQr; //保存图片位置
    private File gifQr; //暂存Gif图片位置

    private Bitmap mCropImage;//裁剪后的图片
    private CropImageView.CropPosSize mCropSize;//裁剪的图片大小
    private CropImageView pickPhoto;//可裁剪的图片的View
    private final static int REQUEST_PICK_IMAGE = 1; //选择图片的
    private GifDrawable mGifDrawable;
    private Bitmap[] gifArray;//原版gif图
    private Bitmap[] QRGifArray;//Gif二维码
    private static final int NORMAL_MODE = 0;
    private static final int PICTURE_MODE = 1;
    private static final int LOGO_MODE = 2;
    private static final int EMBED_MODE = 3;
    private boolean mGif;//是否是gif格式
    private int mCurrentMode = -1;//当前模式

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pickPhoto = (CropImageView) findViewById(R.id.pick_img);
    }

    /**
     * 保存图片
     *
     * @param vivi
     */
    public void save(View vivi) {
        saveQRImage();
    }

    /**
     * 嵌入模式
     *
     * @param vivi
     */
    public void inMode(View vivi) {
        mCurrentMode = EMBED_MODE;
        pickImage();
    }

    /**
     * 图片模式
     *
     * @param vivi
     */
    public void imageMode(View vivi) {
        mCurrentMode = PICTURE_MODE;
        pickImage();
    }

    /**
     * gif实际还是图片格式
     *
     * @param vivi
     */
    public void gifMode(View vivi) {
        mCurrentMode = PICTURE_MODE;
        pickImage();
    }

    /**
     * gif实际还是图片格式
     *
     * @param vivi
     */
    public void logoMode(View vivi) {
        mCurrentMode = LOGO_MODE;
        pickImage();
    }

    /**
     * 制作
     *
     * @param vivi
     */
    public void make(View vivi) {
        new AlertDialog.Builder(this)
                .setTitle("黑白还是彩色")
                .setMessage("Qart 能生成黑白或者彩色的二维码，如果选取彩色的话，请选择合适的颜色以提高识别率，如果选取的颜色太淡或者与选择的图片颜色相似，二维码可能会扫不出来")
                .setPositiveButton(R.string.colorful, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                        //彩色选择颜色
                        chooseColor();
                    }
                })
                .setNegativeButton(R.string.black_white, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                        //黑白的
                        startConvert(false, Color.BLACK);
                    }
                })
                .create()
                .show();
    }

    /**
     * 选择结果
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            //选择图片后
            case REQUEST_PICK_IMAGE:
                if (resultCode == RESULT_OK) {
                    if (mGifDrawable != null) {
                        mGifDrawable.recycle();
                    }
                    try {
                        String mimeType = getContentResolver().getType(data.getData());
                        if (mimeType != null && mimeType.equals("image/gif")) {
                            //gif 格式
                            if (mCurrentMode != PICTURE_MODE) {
                                Toast.makeText(this, "只有图片模式支持 GIF", Toast.LENGTH_LONG).show();
                                return;
                            }
                            mGif = true;
                            mGifDrawable = new GifDrawable(getContentResolver(), data.getData());
                            pickPhoto.setImageDrawable(mGifDrawable);
                            mOriginBitmap = mGifDrawable.seekToFrameAndGet(0);
                            //其他模式
                        } else {
                            mGif = false;
                            mOriginBitmap = getBitmapFromUri(data.getData());
                            convertOrientation(mOriginBitmap, data.getData());
                            pickPhoto.setImageBitmap(mOriginBitmap);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
//                    mPickImage = true;
//                    mCropSize = null;
//                    mCropImage = null;
                    pickPhoto.setShowSelectFrame(true);
                }
                break;
        }
    }

    private final static int MAX_INPUT_BITMAP_WIDTH = 720;
    private final static int MAX_INPUT_BITMAP_HEIGHT = 1280;

    /**
     * 获取选择图片的bitmap
     *
     * @param imageUri
     * @return
     */
    public Bitmap getBitmapFromUri(Uri imageUri) {
        getContentResolver().notifyChange(imageUri, null);
        ContentResolver cr = getContentResolver();
        Bitmap bitmap;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(cr, imageUri);
            float scale = Math.min((float) 1.0 * MAX_INPUT_BITMAP_WIDTH / bitmap.getWidth(), (float) 1.0 * MAX_INPUT_BITMAP_HEIGHT / bitmap.getHeight());
            if (scale < 1) {
                bitmap = ArtQRCode.getResizedBitmap(bitmap, scale, scale);
            }
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean mConverting;

    /**
     * 方向旋转
     */
    public void convertOrientation(Bitmap bitmap, Uri imageUri) {
        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
        Cursor cur = getContentResolver().query(imageUri, orientationColumn, null, null, null);
        int orientation = 0;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
            mOriginBitmap = ArtQRCode.rotateImage(bitmap, orientation);
        }
    }

    /**
     * 准备选择图片
     */
    private void pickImage() {
        if (mConverting) {
            Toast.makeText(this, "正在生成二维码，请稍等", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isStoragePermissionGranted()) {
            launchGallery();
        }
    }

    String TAG = "aaa";

    /**
     * 请求权限
     *
     * @return
     */
    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "Permission is granted");
                return true;
            } else {
                Log.v(TAG, "Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PICK_IMAGE);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG, "Permission is granted");
            return true;
        }
    }

    /**
     * 选择图片
     */
    private void launchGallery() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, REQUEST_PICK_IMAGE);
    }

    /**
     * 生成指定颜色的二维码
     *
     * @param colorful 是否是彩色
     * @param color    颜色值
     */
    private void startConvert(final boolean colorful, final int color) {
        mConverting = true;
        if (mCurrentMode == NORMAL_MODE) {
            mQRBitmap = ArtQRCode.ProductNormal(qrText, colorful, color);
            pickPhoto.setImageBitmap(mQRBitmap);
            mConverting = false;
            return;
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (mGif) {
                    //Gif生成
                    QRGifArray = ArtQRCode.ProductGIF(qrText, gifArray, colorful, color);
                    shareQr = new File(getExternalCacheDir(), "Pictures");
                    if (!shareQr.exists()) {
                        shareQr.mkdirs();
                    }
                    //二维码名称暂存位置
                    gifQr = new File(shareQr, "qrImage.gif");
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(gifQr);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    if (fos != null) {
                        AnimatedGifEncoder gifEncoder = new AnimatedGifEncoder();
                        gifEncoder.setRepeat(0);
                        gifEncoder.start(fos);
                        for (Bitmap bitmap : QRGifArray) {
                            Log.d(TAG, "gifEncoder.addFrame");
                            gifEncoder.addFrame(bitmap);
                        }
                        gifEncoder.finish();
                    }

                } else {
                    switch (mCurrentMode) {
                        //图像模式,即选择图片生成为二维码
                        case PICTURE_MODE:
                            mQRBitmap = ArtQRCode.Product(qrText, mCropImage, colorful, color);
                            break;
                        //logo
                        case LOGO_MODE:
                            mQRBitmap = ArtQRCode.ProductLogo(mCropImage, qrText, colorful, color);
                            break;
                        //嵌入在某一个图片中
                        //     * @param txt 二维码文字
                        //     * @param input 裁剪的图片
                        //     * @param colorful  是否是彩色
                        //     * @param color  颜色值
                        //     * @param x   二维码在图片的x轴
                        //     * @param y 二维码在图片的Y轴
                        //     * @param originBitmap 原图
                        case EMBED_MODE:
                          int x = mCropSize.x;
                             int y = mCropSize.y;
                            mQRBitmap = ArtQRCode.ProductEmbed(qrText, mCropImage, colorful, color, x, y, mOriginBitmap);
                            break;
                        default:
                            break;
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void post) {
                super.onPostExecute(post);
                //裁剪功能设置为不可用
                pickPhoto.setShowSelectFrame(false);
                if (mGif) {
                    try {
                        //加载Gif图片
                        pickPhoto.setImageDrawable(new GifDrawable(gifQr));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    pickPhoto.setImageBitmap(mQRBitmap);
                }
                mConverting = false;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (mGif) {
                    mCropSize = mCropSize == null ? pickPhoto.getCroppedSize(mOriginBitmap) : mCropSize;
                    gifArray = new Bitmap[mGifDrawable.getNumberOfFrames()];
                    for (int i = 0; i < gifArray.length; i++) {
                        gifArray[i] = pickPhoto.getCroppedImage(mGifDrawable.seekToFrameAndGet(i), mCropSize);
                    }
                } else {
                    mCropImage = mCropImage == null ? pickPhoto.getCroppedImage(mOriginBitmap) : mCropImage;
                }
                if (mCurrentMode == EMBED_MODE) {
                    mCropSize = mCropSize == null ? pickPhoto.getCroppedSize(mOriginBitmap) : mCropSize;
                }
            }
        }.execute();
    }

    /**
     * 默认选色板选中的颜色
     */
    private int mColor = Color.rgb(0x28, 0x32, 0x60);
    /**
     * 亮度阀值提醒
     */
    private final static int COLOR_BRIGHTNESS_THRESHOLD = 0x7f;

    /**
     * 选择颜色
     */
    private void chooseColor() {
        ColorPickerDialogBuilder.with(Main2Activity.this)
                .setTitle(R.string.choose_color)
                //  .wheelType(ColorPickerView.AUTOFILL_TYPE_DATE.)
                .initialColor(mColor)  //default blue
                .density(12)
                .lightnessSliderOnly()
                .setPositiveButton(android.R.string.ok, new ColorPickerClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                        //提醒
                        if (selectedColor == Color.WHITE) {
                            Toast.makeText(Main2Activity.this, R.string.select_white, Toast.LENGTH_LONG).show();
                        } else if (ArtQRCodeUtil.calculateColorGrayValue(selectedColor) > COLOR_BRIGHTNESS_THRESHOLD) {
                            Toast.makeText(Main2Activity.this, R.string.select_light, Toast.LENGTH_LONG).show();

                        }
                        mColor = selectedColor;
                        startConvert(true, selectedColor);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startConvert(true, Color.BLACK);
                    }
                })
                .showColorEdit(false)
                .build()
                .show();
    }

    /**
     * 保存图片
     */
    private void saveQRImage() {
        if (mQRBitmap == null) {
            Toast.makeText(this, "保存失败!还未生成", Toast.LENGTH_LONG).show();
            return;
        }
        shareQr = new File(Environment.getExternalStorageDirectory(), "Pictures");
        if (shareQr.exists() == false) {
            shareQr.mkdirs();
        }
        //新文件名称
        File newFile = mGif ? new File(shareQr, "Qart_" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").
                format(new Date()).replaceAll("\\W+", "") + ".gif")
                : new File(shareQr, "Qart_" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").
                format(new Date()).replaceAll("\\W+", "") + ".png");
        //保存下来
        if (mGif) {
            try {
                ArtQRCodeUtil.copy(new File(getExternalCacheDir(), "Pictures/qrImage.gif"), newFile);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            ArtQRCodeUtil.saveBitmap(mQRBitmap, newFile.toString());
        }
        Toast.makeText(this, "已保存到" + newFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
//      Uri uri = Uri.fromFile(newFile);
//      Intent scannerIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
//        sendBroadcast(scannerIntent);
    }
}

