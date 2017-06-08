package net.zsygfddsd.y_qrcode_view_demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import net.zsygfddsd.y_qrcode_view.captureview.Y_I_QrCodeHandleDelegate;
import net.zsygfddsd.y_qrcode_view.captureview.Y_CaptureView;
import net.zsygfddsd.y_qrcode_view.captureview.Y_CaptureViewBuilder;
import net.zsygfddsd.y_qrcode_view.qrcode.view.ViewfinderView;

public class MainActivity extends AppCompatActivity implements Y_I_QrCodeHandleDelegate {

    private SurfaceView surfaceView;
    private ViewfinderView viewfinderView;
    private Y_CaptureView captureView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        //        getSupportActionBar().hide();
        //surfaceView和 viewfinderView 必须大小位置全部重合叠加在一块
        surfaceView = (SurfaceView) findViewById(R.id.capture_preview_view);
        viewfinderView = (ViewfinderView) findViewById(R.id.capture_viewfinder_view);

        captureView = new Y_CaptureViewBuilder(this)
                .setSurfaceView(surfaceView)
                .setViewfinderView(viewfinderView)
                .setDelegate(this)
                .create();

        captureView.onCreate(savedInstanceState);

    }

    @Override
    protected void onResume() {
        super.onResume();
        captureView.onResume();
    }

    @Override
    protected void onPause() {
        captureView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        captureView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onScanQRCodeSuccess(String result) {
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        captureView.resetSurface();
    }
}
