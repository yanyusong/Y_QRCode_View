/*
 * Copyright (C) 2010 ZXing authors
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

import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * 该类的作用是在预览界面加载好后向ui线程发消息
 */
final class PreviewCallback implements Camera.PreviewCallback {

    private static final String TAG = PreviewCallback.class.getSimpleName();

    private final CameraConfigurationManager configManager;
    private Handler previewHandler;
    private int previewMessage;

    PreviewCallback(CameraConfigurationManager configManager) {
        this.configManager = configManager;
    }

    /**
     * 绑定handler，用于发消息到ui线程
     *
     * @param previewHandler
     * @param previewMessage
     */
    void setHandler(Handler previewHandler, int previewMessage) {
        this.previewHandler = previewHandler;
        this.previewMessage = previewMessage;
    }

    /**
     * 持续不断的从camera中获取图像数据，传入该方法的data
     *
     * @param data   从camera获取的每一帧的源图像数据
     * @param camera 相机对象
     */
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Point cameraResolution = configManager.getCameraResolution();//这里获取的即是预览层图像的宽高，px
        Handler thePreviewHandler = previewHandler;
        if (cameraResolution != null && thePreviewHandler != null) {
            //终于在这里将Message发射出去了，在这里将每一帧图像的数据发射出去了，
            //包括图像的宽，图像的高，以及图像的byte[]数据data，分别以int arg1, int arg2, Object obj的形式，发射出去了
            //在{@link net.zsygfddsd.y_scan_qrcode_lib.qrcode.decode.DecodeHandler#handleMessage(Message message)}中接收解析
            Message message = thePreviewHandler.obtainMessage(previewMessage, cameraResolution.x, cameraResolution.y, data);
            message.sendToTarget();
            previewHandler = null;
        } else {
            Log.d(TAG, "Got preview callback, but no handler or resolution available");
        }
    }

}
