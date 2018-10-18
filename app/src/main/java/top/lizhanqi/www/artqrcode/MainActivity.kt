package top.lizhanqi.www.artqrcode

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View
import android.widget.Toast
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import pl.droidsonroids.gif.GifDrawable
import top.lizhanqi.www.artcore.AnimatedGifEncoder
import top.lizhanqi.www.artcore.ArtQRCode
import top.lizhanqi.www.artcore.ArtQRCodeUtil
import top.lizhanqi.www.cropperlib.CropImageView
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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
class MainActivity : Activity() {
    internal var qrText = "我是素玄06,工号sx-06"
    private var mOriginBitmap: Bitmap? = null//原版图
    private var mQRBitmap: Bitmap? = null//二维码图
    private var shareQr: File? = null //保存图片位置
    private var gifQr: File? = null //暂存Gif图片位置

    private var mCropImage: Bitmap? = null//裁剪后的图片
    private var mCropSize: CropImageView.CropPosSize? = null//裁剪的图片大小
    private var pickPhoto: CropImageView? = null//可裁剪的图片的View
    private var mGifDrawable: GifDrawable? = null
    private var gifArray: Array<Bitmap?>? = null//原版gif图
    private var QRGifArray: Array<Bitmap>? = null//Gif二维码
    private var mGif: Boolean = false//是否是gif格式
    private var mCurrentMode = -1//当前模式

    private var mConverting: Boolean = false

    internal var TAG = "aaa"

    /**
     * 请求权限
     *
     * @return
     */
    //permission is automatically granted on sdk<23 upon installation
    val isStoragePermissionGranted: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= 23) {
                if ((checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                    Log.v(TAG, "Permission is granted")
                    return true
                } else {
                    Log.v(TAG, "Permission is revoked")
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_PICK_IMAGE
                    )
                    return false
                }
            } else {
                Log.v(TAG, "Permission is granted")
                return true
            }
        }

    /**
     * 默认选色板选中的颜色
     */
    private var mColor = Color.rgb(0x28, 0x32, 0x60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pickPhoto = findViewById<View>(R.id.pick_img) as CropImageView
    }

    /**
     * 保存图片
     *
     * @param vivi
     */
    fun save(vivi: View) {
        saveQRImage()
    }

    /**
     * 嵌入模式
     *
     * @param vivi
     */
    fun inMode(vivi: View) {
        mCurrentMode = EMBED_MODE
        pickImage()
    }

    /**
     * 图片模式
     *
     * @param vivi
     */
    fun imageMode(vivi: View) {
        mCurrentMode = PICTURE_MODE
        pickImage()
    }

    /**
     * gif实际还是图片格式
     *
     * @param vivi
     */
    fun gifMode(vivi: View) {
        mCurrentMode = PICTURE_MODE
        pickImage()
    }

    /**
     * gif实际还是图片格式
     *
     * @param vivi
     */
    fun logoMode(vivi: View) {
        mCurrentMode = LOGO_MODE
        pickImage()
    }

    /**
     * 制作
     *
     * @param vivi
     */
    fun make(vivi: View) {
        AlertDialog.Builder(this)
            .setTitle("黑白还是彩色")
            .setMessage("Qart 能生成黑白或者彩色的二维码，如果选取彩色的话，请选择合适的颜色以提高识别率，如果选取的颜色太淡或者与选择的图片颜色相似，二维码可能会扫不出来")
            .setPositiveButton(R.string.colorful) { dialogInterface, i ->
                dialogInterface.cancel()
                //彩色选择颜色
                chooseColor()
            }
            .setNegativeButton(R.string.black_white) { dialogInterface, i ->
                dialogInterface.cancel()
                //黑白的
                startConvert(false, Color.BLACK)
            }
            .create()
            .show()
    }

    /**
     * 选择结果
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            //选择图片后
            REQUEST_PICK_IMAGE -> if (resultCode == Activity.RESULT_OK) {
                if (mGifDrawable != null) {
                    mGifDrawable!!.recycle()
                }
                try {
                    val mimeType = contentResolver.getType(data.data!!)
                    if (mimeType != null && mimeType == "image/gif") {
                        //gif 格式
                        if (mCurrentMode != PICTURE_MODE) {
                            Toast.makeText(this, "只有图片模式支持 GIF", Toast.LENGTH_LONG).show()
                            return
                        }
                        mGif = true
                        mGifDrawable = GifDrawable(contentResolver, data.data!!)
                        pickPhoto!!.setImageDrawable(mGifDrawable)
                        mOriginBitmap = mGifDrawable!!.seekToFrameAndGet(0)
                        //其他模式
                    } else {
                        mGif = false
                        mOriginBitmap = getBitmapFromUri(data.data)
                        convertOrientation(mOriginBitmap, data.data)
                        pickPhoto!!.setImageBitmap(mOriginBitmap)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                //                    mPickImage = true;
                //                    mCropSize = null;
                //                    mCropImage = null;
                pickPhoto!!.isShowSelectFrame = true
            }
        }
    }

    /**
     * 获取选择图片的bitmap
     *
     * @param imageUri
     * @return
     */
    fun getBitmapFromUri(imageUri: Uri?): Bitmap? {
        contentResolver.notifyChange(imageUri!!, null)
        val cr = contentResolver
        var bitmap: Bitmap
        try {
            bitmap = MediaStore.Images.Media.getBitmap(cr, imageUri)
            val scale = Math.min(
                1.0.toFloat() * MAX_INPUT_BITMAP_WIDTH / bitmap.width,
                1.0.toFloat() * MAX_INPUT_BITMAP_HEIGHT / bitmap.height
            )
            if (scale < 1) {
                bitmap = ArtQRCode.getResizedBitmap(bitmap, scale, scale)
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

    }

    /**
     * 方向旋转
     */
    fun convertOrientation(bitmap: Bitmap?, imageUri: Uri?) {
        val orientationColumn = arrayOf(MediaStore.Images.Media.ORIENTATION)
        val cur = contentResolver.query(imageUri!!, orientationColumn, null, null, null)
        var orientation = 0
        if (cur != null && cur!!.moveToFirst()) {
            orientation = cur!!.getInt(cur!!.getColumnIndex(orientationColumn[0]))
            mOriginBitmap = ArtQRCode.rotateImage(bitmap, orientation.toFloat())
        }
    }

    /**
     * 准备选择图片
     */
    private fun pickImage() {
        if (mConverting) {
            Toast.makeText(this, "正在生成二维码，请稍等", Toast.LENGTH_SHORT).show()
            return
        }
        if (isStoragePermissionGranted) {
            launchGallery()
        }
    }

    /**
     * 选择图片
     */
    private fun launchGallery() {
        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        startActivityForResult(photoPickerIntent, REQUEST_PICK_IMAGE)
    }

    /**
     * 生成指定颜色的二维码
     *
     * @param colorful 是否是彩色
     * @param color    颜色值
     */
    private fun startConvert(colorful: Boolean, color: Int) {
        mConverting = true
        if (mCurrentMode == NORMAL_MODE) {
            mQRBitmap = ArtQRCode.ProductNormal(qrText, colorful, color)
            pickPhoto!!.setImageBitmap(mQRBitmap)
            mConverting = false
            return
        }
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void?): Void? {
                if (mGif) {
                    //Gif生成
                    QRGifArray = ArtQRCode.ProductGIF(qrText, gifArray!!, colorful, color)
                    shareQr = File(externalCacheDir, "Pictures")
                    if (!shareQr!!.exists()) {
                        shareQr!!.mkdirs()
                    }
                    //二维码名称暂存位置
                    gifQr = File(shareQr, "qrImage.gif")
                    var fos: FileOutputStream? = null
                    try {
                        fos = FileOutputStream(gifQr!!)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    }

                    if (fos != null) {
                        val gifEncoder = AnimatedGifEncoder()
                        gifEncoder.setRepeat(0)
                        gifEncoder.start(fos)
                        for (bitmap in QRGifArray!!) {
                            Log.d(TAG, "gifEncoder.addFrame")
                            gifEncoder.addFrame(bitmap)
                        }
                        gifEncoder.finish()
                    }

                } else {
                    when (mCurrentMode) {
                        //图像模式,即选择图片生成为二维码
                        PICTURE_MODE -> mQRBitmap = ArtQRCode.Product(qrText, mCropImage!!, colorful, color)
                        //logo
                        LOGO_MODE -> mQRBitmap = ArtQRCode.ProductLogo(mCropImage!!, qrText, colorful, color)
                        //嵌入在某一个图片中
                        //     * @param txt 二维码文字
                        //     * @param input 裁剪的图片
                        //     * @param colorful  是否是彩色
                        //     * @param color  颜色值
                        //     * @param x   二维码在图片的x轴
                        //     * @param y 二维码在图片的Y轴
                        //     * @param originBitmap 原图
                        EMBED_MODE -> {
                            val x = mCropSize!!.x
                            val y = mCropSize!!.y
                            mQRBitmap =
                                    ArtQRCode.ProductEmbed(qrText, mCropImage!!, colorful, color, x, y, mOriginBitmap)
                        }
                        else -> {
                        }
                    }
                }
                return null
            }

            override fun onPostExecute(post: Void?) {
                super.onPostExecute(post)
                //裁剪功能设置为不可用
                pickPhoto!!.isShowSelectFrame = false
                if (mGif) {
                    try {
                        //加载Gif图片
                        pickPhoto!!.setImageDrawable(GifDrawable(gifQr!!))
                    } catch (ex: IOException) {
                        ex.printStackTrace()
                    }

                } else {
                    pickPhoto!!.setImageBitmap(mQRBitmap)
                }
                mConverting = false
            }

            override fun onPreExecute() {
                super.onPreExecute()
                if (mGif) {
                    mCropSize = if (mCropSize == null) pickPhoto!!.getCroppedSize(mOriginBitmap) else mCropSize
                    gifArray=    arrayOfNulls<Bitmap> ( mGifDrawable!!.numberOfFrames )
                    for (i in gifArray!!.indices) {
                        gifArray!![i] = pickPhoto!!.getCroppedImage(mGifDrawable!!.seekToFrameAndGet(i), mCropSize)
                    }
                } else {
                    mCropImage = if (mCropImage == null) pickPhoto!!.getCroppedImage(mOriginBitmap) else mCropImage
                }
                if (mCurrentMode == EMBED_MODE) {
                    mCropSize = if (mCropSize == null) pickPhoto!!.getCroppedSize(mOriginBitmap) else mCropSize
                }
               }
        }.execute()
    }

    /**
     * 选择颜色
     */
    private fun chooseColor() {
        ColorPickerDialogBuilder.with(this@MainActivity)
            .setTitle(R.string.choose_color)
            //  .wheelType(ColorPickerView.AUTOFILL_TYPE_DATE.)
            .initialColor(mColor)  //default blue
            .density(12)
            .lightnessSliderOnly()
            .setPositiveButton(
                android.R.string.ok
            ) { dialog, selectedColor, allColors ->
                //提醒
                if (selectedColor == Color.WHITE) {
                    Toast.makeText(this@MainActivity, R.string.select_white, Toast.LENGTH_LONG).show()
                } else if (ArtQRCodeUtil.calculateColorGrayValue(selectedColor) > COLOR_BRIGHTNESS_THRESHOLD) {
                    Toast.makeText(this@MainActivity, R.string.select_light, Toast.LENGTH_LONG).show()

                }
                mColor = selectedColor
                startConvert(true, selectedColor)
            }
            .setNegativeButton(android.R.string.cancel, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    startConvert(true, Color.BLACK)
                }
            })
            .showColorEdit(false)
            .build()
            .show()
    }

    /**
     * 保存图片
     */
    private fun saveQRImage() {
        if (mQRBitmap == null) {
            Toast.makeText(this, "保存失败!还未生成", Toast.LENGTH_LONG).show()
            return
        }
        shareQr = File(Environment.getExternalStorageDirectory(), "Pictures")
        if (shareQr!!.exists() == false) {
            shareQr!!.mkdirs()
        }
        //新文件名称
        val newFile = if (mGif)
            File(
                shareQr,
                ("Qart_" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()).replace(
                    ("\\W+").toRegex(),
                    ""
                ) + ".gif")
            )
        else
            File(
                shareQr,
                ("Qart_" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()).replace(
                    ("\\W+").toRegex(),
                    ""
                ) + ".png")
            )
        //保存下来
        if (mGif) {
            try {
                ArtQRCodeUtil.copy(File(externalCacheDir, "Pictures/qrImage.gif"), newFile)
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

        } else {
            ArtQRCodeUtil.saveBitmap(mQRBitmap!!, newFile.toString())
        }
        Toast.makeText(this, "已保存到" + newFile.absolutePath, Toast.LENGTH_LONG).show()
        //      Uri uri = Uri.fromFile(newFile);
        //      Intent scannerIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        //        sendBroadcast(scannerIntent);
    }

    companion object {
        private val REQUEST_PICK_IMAGE = 1 //选择图片的
        private val NORMAL_MODE = 0
        private val PICTURE_MODE = 1
        private val LOGO_MODE = 2
        private val EMBED_MODE = 3

        private val MAX_INPUT_BITMAP_WIDTH = 720
        private val MAX_INPUT_BITMAP_HEIGHT = 1280
        /**
         * 亮度阀值提醒
         */
        private val COLOR_BRIGHTNESS_THRESHOLD = 0x7f
    }
}


