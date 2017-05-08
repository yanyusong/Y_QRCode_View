/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.zsygfddsd.y_scan_qrcode_lib.qrcode.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;

import net.zsygfddsd.y_scan_qrcode_lib.R;

import java.io.IOException;


/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding. <br/>
 * <br/>
 * <p/>
 * 该类封装了相机的所有服务并且是该app中唯一与相机打交道的类
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

    private static final String TAG = CameraManager.class.getSimpleName();

    private final Context context;

    private final CameraConfigurationManager configManager;

    private Camera camera;

    private AutoFocusManager autoFocusManager;

    private float rectWidthScale = 0.5f;//正方形扫描框的宽度相对于屏幕小边的比例，比如：720*1280，即是720的边，改变这个值可以改变扫描框的大小

    private Rect framingRect;

    private Rect framingRectOnScreen;

    private boolean initialized;

    private boolean previewing;

    private int requestedFramingRectWidth;

    private int requestedFramingRectHeight;

    /**
     * Preview frames are delivered here, which we pass on to the registered
     * handler. Make sure to clear the handler so it will only receive one
     * message.
     */
    private final PreviewCallback previewCallback;

    public CameraManager(Context context) {
        this.context = context;
        TypedValue outValue = new TypedValue();
        context.getResources().getValue(R.dimen.rect_width_scale, outValue, true);
        this.rectWidthScale = outValue.getFloat();
        this.configManager = new CameraConfigurationManager(context);
        previewCallback = new PreviewCallback(configManager);
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames
     *               into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    public synchronized void openDriver(SurfaceHolder holder)
            throws IOException {
        Camera theCamera = camera;
        if (theCamera == null) {
            // 获取手机背面的摄像头
            theCamera = OpenCameraInterface.open();
            if (theCamera == null) {
                throw new IOException();
            }
            camera = theCamera;
        }

        // 设置摄像头预览view
        theCamera.setPreviewDisplay(holder);
        theCamera.lock();

        if (!initialized) {
            initialized = true;
            configManager.initFromCameraParameters(theCamera);
            if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
                setManualFramingRect(requestedFramingRectWidth,
                        requestedFramingRectHeight);
                requestedFramingRectWidth = 0;
                requestedFramingRectHeight = 0;
            }
        }

        Camera.Parameters parameters = theCamera.getParameters();
        String parametersFlattened = parameters == null ? null : parameters
                .flatten(); // Save
        // these,
        // temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera, false);
        } catch (RuntimeException re) {
            // Driver failed
            Log.w(TAG,
                    "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved camera params: "
                    + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = theCamera.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    theCamera.setParameters(parameters);
                    configManager.setDesiredCameraParameters(theCamera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG,
                            "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }

    }

    public synchronized boolean isOpen() {
        return camera != null;
    }

    /**
     * Closes the camera driver if still in use.
     */
    public synchronized void closeDriver() {
        if (camera != null) {
            camera.release();
            camera = null;
            // Make sure to clear these each time we close the camera, so that
            // any scanning rect
            // requested by intent is forgotten.
            framingRect = null;
            framingRectOnScreen = null;
        }
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public synchronized void startPreview() {
        Camera theCamera = camera;
        if (theCamera != null && !previewing) {
            // Starts capturing and drawing preview frames to the screen
            // Preview will not actually start until a surface is supplied with
            // setPreviewDisplay(SurfaceHolder) or
            // setPreviewTexture(SurfaceTexture).
            theCamera.startPreview();

            previewing = true;
            autoFocusManager = new AutoFocusManager(context, camera);
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public synchronized void stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
            autoFocusManager = null;
        }
        if (camera != null && previewing) {
            camera.stopPreview();
            previewCallback.setHandler(null, 0);
            previewing = false;
        }
    }

    /**
     * Convenience method for
     * {@link }
     */
    public synchronized void setTorch(boolean newSetting) {
        if (newSetting != configManager.getTorchState(camera)) {
            if (camera != null) {
                if (autoFocusManager != null) {
                    autoFocusManager.stop();
                }
                configManager.setTorch(camera, newSetting);
                if (autoFocusManager != null) {
                    autoFocusManager.start();
                }
            }
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data
     * will arrive as byte[] in the message.obj field, with width and height
     * encoded as message.arg1 and message.arg2, respectively. <br/>
     * <p/>
     * 两个绑定操作：<br/>
     * 1：将handler与回调函数绑定；<br/>
     * 2：将相机与回调函数绑定<br/>
     * 综上，该函数的作用是当相机的预览界面准备就绪后就会调用hander向其发送传入的message
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    public synchronized void requestPreviewFrame(Handler handler, int message) {
        Camera theCamera = camera;
        if (theCamera != null && previewing) {
            previewCallback.setHandler(handler, message);

            // 绑定相机回调函数，当预览界面准备就绪后会回调Camera.PreviewCallback.onPreviewFrame
            //取一帧的预览图像
            theCamera.setOneShotPreviewCallback(previewCallback);
        }
    }

    /**
     * Calculates the framing rect which the UI should draw to show the user
     * where to place the barcode. This target helps with alignment as well as
     * forces the user to hold the device far enough away to ensure the image
     * will be in focus.
     * <p>
     * 这里最重要的有两点要区分开来，这个方法返回的是
     * 画扫描区域的正方形在屏幕window坐标下的坐标，
     * 还有一个是识别区域的正方形的坐标，{@link #getFramingRectOnScreen()}
     * 这两个一定要区分开来，
     * 一个是看的的区域，一个是实际计算识别时的区域
     * 这个看的区域是在ViewfinderView层的，识别区域取图像的方法getFramingRectInPreview是在
     * surfaceView预览层的
     *
     * @return The rectangle to draw on screen in window coordinates.
     * 原来这里最大的坑是The rectangle是基于window coordinates即window坐标系的，
     * 所以图像层surfaceView和覆盖层ViewfinderView都必须是统一的
     */
    //    public synchronized Rect getFramingRect() {
    //        if (framingRect == null) {
    //            if (camera == null) {
    //                return null;
    //            }
    //            //得到手机屏幕的分辨率，例如：720*1280这种的
    //            Point screenResolution = configManager.getScreenResolution();
    //            if (screenResolution == null) {
    //                // Called early, before init even finished
    //                return null;
    //            }
    //            if (rectWidthScale > 1) {
    //                throw new IllegalArgumentException("rect_width_scale must smaller than 1");
    //            }
    //            //取屏幕的较小边乘以比例算出正方形的边长
    //            int width = (int) ((screenResolution.x < screenResolution.y ? screenResolution.x : screenResolution.y) * rectWidthScale);
    //            // 将扫描框设置成一个正方形
    //            int height = width;
    //            //识别区域是屏幕正中，-150是为了往上移一点，150像素=50dp（导航栏高度）+25dp（状态栏高度）
    //            //这样的话，假如有导航栏和状态栏，扫描区域就正好在屏幕正中了
    //            int leftOffset = (screenResolution.x - width) / 2;
    //            int topOffset = (screenResolution.y - height) / 2 - 150;
    //            //            int topOffset = (screenResolution.y - height) / 2;
    //            framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
    //                    topOffset + height);
    //
    //            Log.d(TAG, "Calculated framing rect: " + framingRect);
    //        }
    //
    //        return framingRect;
    //    }

    /**
     * 计算出Rect在viewFinderView坐标系中的坐标,即framingRect
     *
     * @param vfvWidth
     * @param vfvHeight
     * @return
     */
    public synchronized Rect getFramingRect(int vfvWidth, int vfvHeight) {
        if (framingRect == null) {
            if (camera == null) {
                return null;
            }
            if (rectWidthScale > 1) {
                throw new IllegalArgumentException("rect_width_scale must smaller than 1");
            }
            //取屏幕的较小边乘以比例算出正方形的边长
            int width = (int) ((vfvWidth < vfvHeight ? vfvWidth : vfvHeight) * rectWidthScale);
            // 将扫描框设置成一个正方形
            int height = width;
            //识别区域在ViewFinderView的画布正中
            int leftOffset = (vfvWidth - width) / 2;
            int topOffset = (vfvHeight - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
                    topOffset + height);

            Log.d(TAG, "Calculated framing rect: " + framingRect);

        }
        return framingRect;
    }

    public synchronized Rect setFramingRectOnScreen(int left, int top, int right, int bottom) {
        if (framingRectOnScreen == null) {
            Rect rect = new Rect();
            //            Point cameraResolution = configManager.getCameraResolution();
            //            Point screenResolution = configManager.getScreenResolution();
            //            if (cameraResolution == null || screenResolution == null) {
            //                // Called early, before init even finished
            //                return null;
            //            }
            //            //好像是为了纠正相机取的图像分辨率与屏幕分辨率颠倒吧
            //            rect.left = rect.left * cameraResolution.y / screenResolution.x;
            //            rect.right = rect.right * cameraResolution.y / screenResolution.x;
            //            rect.top = rect.top * cameraResolution.x / screenResolution.y;
            //            rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
            rect.left = left;
            rect.right = right;
            rect.top = top;
            rect.bottom = bottom;
            framingRectOnScreen = rect;

            Log.d(TAG, "Calculated framingRectOnScreen rect: "
                    + framingRectOnScreen);
        }
        return framingRectOnScreen;
    }

    public synchronized Rect getFramingRectOnScreen() {

        return framingRectOnScreen;
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions,
     * rather than determine them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    public synchronized void setManualFramingRect(int width, int height) {
        if (initialized) {
            Point screenResolution = configManager.getScreenResolution();
            if (width > screenResolution.x) {
                width = screenResolution.x;
            }
            if (height > screenResolution.y) {
                height = screenResolution.y;
            }
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
                    topOffset + height);
            Log.d(TAG, "Calculated manual framing rect: " + framingRect);
            framingRectOnScreen = null;
        } else {
            requestedFramingRectWidth = width;
            requestedFramingRectHeight = height;
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data,
                                                         int width, int height) {
        Rect rect = getFramingRectOnScreen();
        if (rect == null) {
            return null;
        }
        //为了最好的体验
        //得保证三个区域的统一，第一扫描框的区域、第二拿去识别的区域、第三识别后拿来展示的区域

        Log.d(TAG, "获取到的源图像宽高是----" + width + "---" + height + "----要采集的区域是--" + rect.toString());
        // Go ahead and assume it's YUV rather than die.
        //data 采集到的图像源数据是屏幕大小的例如720*1280，
        // rect是要处理截取的图像区域，所以rect坐标系一定要转换到屏幕坐标系下
        return new PlanarYUVLuminanceSource(data, width, height, rect.left,
                rect.top, rect.width(), rect.height(), false);
    }

    /**
     * 焦点放小
     */
    public void zoomOut() {
        if (camera != null && camera.getParameters().isZoomSupported()) {

            Camera.Parameters parameters = camera.getParameters();
            if (parameters.getZoom() <= 0) {
                return;
            }

            parameters.setZoom(parameters.getZoom() - 1);
            camera.setParameters(parameters);

        }
    }

    /**
     * 焦点放大
     */
    public void zoomIn() {
        if (camera != null && camera.getParameters().isZoomSupported()) {

            Camera.Parameters parameters = camera.getParameters();
            if (parameters.getZoom() >= parameters.getMaxZoom()) {
                return;
            }

            parameters.setZoom(parameters.getZoom() + 1);
            camera.setParameters(parameters);

        }
    }

    /*
     * 缩放
     *
     * @param scale
     */
    public void setCameraZoom(int scale) {
        if (camera != null && camera.getParameters().isZoomSupported()
                && scale <= camera.getParameters().getMaxZoom() && scale >= 0) {

            Camera.Parameters parameters = camera.getParameters();

            parameters.setZoom(scale);
            camera.setParameters(parameters);

        }
    }

    /**
     * dp转px
     *
     * @param context
     * @param dipValue
     * @return
     */
    public int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

}
