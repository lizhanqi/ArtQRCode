  
   
   使用方式demo中有,kotlin与java版本
   (目前demo存在一处bug,就是第一次生成位置准确,第二次以后就错位了,有时间在修复,不是依赖库的问题放心使用)
 * 生成的核心方法:   最好异步,防止ANR
 * //图像模式,即选择图片生成为二维码
 * mQRBitmap = ArtQRCode.Product(qrText, mCropImage, colorful, color);
 * logo
 * mQRBitmap = ArtQRCode.ProductLogo(mCropImage, qrText, colorful, color);
 * 嵌入在某一个图片中
 * mQRBitmap = ArtQRCode.ProductEmbed(qrText, mCropImage, colorful, color, mCropSize.x, mCropSize.y, mOriginBitmap);
 * Gif
 * ArtQRCode.ProductGIF(qrText, gifArray, colorful, color);
 
 
 
# 安卓二维码Gif版
## Demo
![](https://github.com/lizhanqi/ArtQRCode/blob/master/pictrue/7b00b7df1941a69e0ff9ca043ca2086b.mp4)
![](https://github.com/lizhanqi/ArtQRCode/blob/master/pictrue/GIF.gif)
![](https://github.com/lizhanqi/ArtQRCode/blob/master/pictrue/内嵌.jpg)
![](https://github.com/lizhanqi/ArtQRCode/blob/master/pictrue/logo.gif)
![](https://github.com/lizhanqi/ArtQRCode/blob/master/pictrue/整图.jpg)

 
    感谢大神：https://github.com/chinuno-usami/CuteR
 

