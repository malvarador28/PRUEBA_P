package com.example.prueba_p;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.prueba_p.ml.Modelobandera;
//import com.example.realtimefacedetection.ml.Model2;
//import com.example.realtimefacedetection.ml.Model3;
//import com.example.realtimefacedetection.ml.Model9noIT;
//import com.example.realtimefacedetection.ml.Modelobandera;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import CameraX.CameraConnectionFragment;
import CameraX.ImageUtils;

public class MainActivity extends AppCompatActivity implements OnSuccessListener<Text>, OnFailureListener , ImageReader.OnImageAvailableListener{
    public static int REQUEST_CAMERA = 111;
    public static int REQUEST_GALLERY = 222;

    public Bitmap mSelectedImage;
    public ImageView mImageView;
    public TextView txtResults;
    public Button btCamera, btGaleria;

    private ObjectDetector DetectorObjecto;
    public String texto;

    ArrayList<String> permisosNoAprobados;
    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtResults = findViewById(R.id.txtresults);
        //mImageView = findViewById(R.id.image_view);
        btCamera = findViewById(R.id.btCamera);
        btGaleria = findViewById(R.id.btGallery);

        ArrayList<String> permisos_requeridos = new ArrayList<String>();
        permisos_requeridos.add(android.Manifest.permission.CAMERA);
        permisos_requeridos.add(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        permisos_requeridos.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);

        permisosNoAprobados  = getPermisosNoAprobados(permisos_requeridos);

        requestPermissions(permisosNoAprobados.toArray(new String[permisosNoAprobados.size()]),
                100);
    }

    int previewHeight = 0,previewWidth = 0;
    int sensorOrientation;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void setFragment() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            cameraId = manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        CameraConnectionFragment fragment;
        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                sensorOrientation = rotation - getScreenOrientation();
                            }
                        },
                        this,   R.layout.camera_fragment,
                        new Size(640, 480));

        camera2Fragment.setCamera(cameraId);
        fragment = camera2Fragment;
        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(android.Manifest.permission.CAMERA)){
                btCamera.setEnabled(grantResults[i] == PackageManager.PERMISSION_GRANTED);
            } else if(permissions[i].equals(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) ||
                    permissions[i].equals(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            ) {
                btGaleria.setEnabled(grantResults[i] == PackageManager.PERMISSION_GRANTED);
            }
        }
    }


    public ArrayList<String> getPermisosNoAprobados(ArrayList<String>  listaPermisos) {
        ArrayList<String> list = new ArrayList<String>();
        Boolean habilitado;


        if (Build.VERSION.SDK_INT >= 23)
            for(String permiso: listaPermisos) {
                if (checkSelfPermission(permiso) != PackageManager.PERMISSION_GRANTED) {
                    list.add(permiso);
                    habilitado = false;
                }else
                    habilitado=true;

                if(permiso.equals(android.Manifest.permission.CAMERA))
                    btCamera.setEnabled(habilitado);
                else if (permiso.equals(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE)  ||
                        permiso.equals(Manifest.permission.READ_EXTERNAL_STORAGE))
                    btGaleria.setEnabled(habilitado);

            }


        return list;
    }

  /*  public void abrirGaleria (View view){
        Intent i = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, REQUEST_GALLERY);
    }*/

    public void abrirCamara (View view){
        setFragment();

        //Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //startActivityForResult(intent, REQUEST_CAMERA);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && null != data) {
            try {
                if (requestCode == REQUEST_CAMERA)
                    mSelectedImage = (Bitmap) data.getExtras().get("data");
                else
                    mSelectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());

                mImageView.setImageBitmap(mSelectedImage);
                PersonalizedModel(null);


            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void PersonalizedModel(View v) {
        try {
            Modelobandera model = Modelobandera.newInstance(getApplicationContext());
            TensorImage image = TensorImage.fromBitmap(mSelectedImage);

            Modelobandera.Outputs outputs = model.process(image);
            List<Category> probability = outputs.getProbabilityAsCategoryList();

            Collections.sort(probability, new CategoryComparator());

            String res="";
            for (int i = 0; i < probability.size(); i++) {
                res = res + probability.get(i).getLabel() +  " " +  probability.get(i).getScore()*100 + " % \n";
            }
            txtResults.setText(res);
            model.close();
        } catch (IOException e) {
            txtResults.setText("Error al procesar Modelo");
        }
    }


 /*  public void Objectofx(View v) {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        labeler.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                    @Override
                    public void onSuccess(List<ImageLabel> labels) {
                        texto="";
                        for (ImageLabel label : labels) {
                            texto += "Objeto: " + label.getIndex() + "  " +label.getText()+"\nConfianza: "+label.getConfidence()*100+"%"+"\n";
                        }

                        txtResults.setText(texto);
                        txtResults.setMovementMethod(new ScrollingMovementMethod());

                    }
                })
                .addOnFailureListener(this);
    }
    public void Rostrosfx(View  v) {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();

        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        if (faces.size() == 0) {
                            txtResults.setText("No Hay rostros");
                        }else{
                            txtResults.setText("Hay " + faces.size() + " rostro(s)");
                        }
                    }
                })
                .addOnFailureListener(this);
    }*/


    public void OCRfx(View v) {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(this)
                .addOnFailureListener(this);
    }


    @Override
    public void onFailure(@NonNull Exception e) {

    }

    @Override
    public void onSuccess(Text text) {
        List<Text.TextBlock> blocks = text.getTextBlocks();
        String resultados="";
        if (blocks.size() == 0) {
            resultados = "No hay Texto";
        }else{
            for (int i = 0; i < blocks.size(); i++) {
                List<Text.Line> lines = blocks.get(i).getLines();
                for (int j = 0; j < lines.size(); j++) {
                    List<Text.Element> elements = lines.get(j).getElements();
                    for (int k = 0; k < elements.size(); k++) {
                        resultados = resultados + elements.get(k).getText() + " ";
                    }
                }
            }
            resultados=resultados + "\n";
        }
        txtResults.setText(resultados);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {

        if (previewWidth == 0 || previewHeight == 0)           return;
        if (rgbBytes == null)    rgbBytes = new int[previewWidth * previewHeight];
        try {
            final Image image = reader.acquireLatestImage();
            if (image == null)    return;
            if (isProcessingFrame) {           image.close();            return;         }

            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =  new Runnable() {
                @Override
                public void run() {
                    ImageUtils.convertYUV420ToARGB8888( yuvBytes[0], yuvBytes[1], yuvBytes[2], previewWidth,  previewHeight,
                            yRowStride,uvRowStride, uvPixelStride,rgbBytes);
                }
            };
            postInferenceCallback =      new Runnable() {
                @Override
                public void run() {  image.close(); isProcessingFrame = false;  }
            };

            processImage();

        } catch (final Exception e) {    }
    }



    private void processImage() {
        imageConverter.run();

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        try {
            Modelobandera model = Modelobandera.newInstance(getApplicationContext());
            TensorImage image = TensorImage.fromBitmap(rgbFrameBitmap);

            Modelobandera.Outputs outputs = model.process(image);
            List<Category> probability = outputs.getProbabilityAsCategoryList();
            Collections.sort(probability, new CategoryComparator());
            String res="";
            for (int i = 0; i < probability.size(); i++)
                res = res + probability.get(i).getLabel() +  " " +  probability.get(i).getScore()*100 + " % \n";

            txtResults.setText(res);
            model.close();
        } catch (IOException e) {
            txtResults.setText("Error al procesar Modelo");
        }

        postInferenceCallback.run();
    }
    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

}

class CategoryComparator implements java.util.Comparator<Category> {
    @Override
    public int compare(Category a, Category b) {
        return (int)(b.getScore()*100) - (int)(a.getScore()*100);
    }
}