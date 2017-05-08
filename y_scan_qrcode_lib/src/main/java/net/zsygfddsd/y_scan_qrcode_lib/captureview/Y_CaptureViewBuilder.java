package net.zsygfddsd.y_scan_qrcode_lib.captureview;

import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;

import net.zsygfddsd.y_scan_qrcode_lib.qrcode.view.ViewfinderView;

/**
 * Created by mac on 2017/3/28.
 */

public class Y_CaptureViewBuilder {

    private AppCompatActivity context;

    private SurfaceView surfaceView;

    private ViewfinderView viewfinderView;

    private Y_I_QrCodeHandleDelegate delegate;

    public Y_CaptureViewBuilder(AppCompatActivity context) {
        this.context = context;
    }

    public Y_CaptureViewBuilder setSurfaceView(SurfaceView surfaceView) {
        this.surfaceView = surfaceView;
        return this;
    }

    public Y_CaptureViewBuilder setViewfinderView(ViewfinderView viewfinderView) {
        this.viewfinderView = viewfinderView;
        return this;
    }

    public Y_CaptureViewBuilder setDelegate(Y_I_QrCodeHandleDelegate delegate) {
        this.delegate = delegate;
        return this;
    }

    public Y_CaptureView create() {
        Y_CaptureView y_captureView = new Y_CaptureView(context);
        if (this.surfaceView == null) {
            throw new IllegalArgumentException("surfaceView must not be null!");
        }
        if (this.viewfinderView == null) {
            throw new IllegalArgumentException("viewfinderView must not be null!");
        }
        y_captureView.surfaceView = this.surfaceView;
        y_captureView.viewfinderView = this.viewfinderView;
        y_captureView.delegate = this.delegate;
        return y_captureView;
    }

}
