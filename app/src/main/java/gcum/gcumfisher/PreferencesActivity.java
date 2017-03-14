package gcum.gcumfisher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class PreferencesActivity extends Activity {

    private static final String PREFERENCES = "gcum.gcumfisher.PREFERENCES";
    private static final String IMAGE_SIZE = "image_size";
    private static final String IMAGE_QUALITY = "image_quality";

    enum ImageSize {
        Small(R.id.images_size_small, 400),
        Medium(R.id.images_size_medium, 800),
        Maximum(R.id.images_size_maximum, Integer.MAX_VALUE);
        private final int id;
        private final int maxSize;

        ImageSize(int id, int maxSize) {
            this.id = id;
            this.maxSize = maxSize;
        }

        @NonNull
        public byte[] resize(@NonNull Photo photo, int quality, @NonNull Context context) throws IOException {
            final Bitmap bitmap = photo.getBitmap(maxSize);
            File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os);
            os.flush();
            os.close();
            return os.toByteArray();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preferences);

        final ImageSize imageSize = getImageSize(getApplicationContext());
        final RadioButton imageSizeButton = (RadioButton) findViewById(imageSize.id);
        imageSizeButton.setChecked(true);

        final SeekBar imageQualityBar = (SeekBar) findViewById(R.id.images_quality);
        final int imageQuality = getImageQuality(getApplicationContext());
        imageQualityBar.setProgress(imageQuality);
        updateImageQualityText(imageQuality);
        imageQualityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setImageQuality(getApplicationContext(), progress);
                updateImageQualityText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        ((TextView) findViewById(R.id.info)).setText(getString(R.string.base_url));
    }

    private void updateImageQualityText (int quality) {
        ((TextView)findViewById(R.id.images_quality_text)).setText(getString(R.string.images_quality_result, quality));
    }

    public void back(View view) {
        finish();
    }

    public void onImageSizeButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        if (checked) for (ImageSize size : ImageSize.values())
            if (size.id == view.getId()) setImageSize(getApplicationContext(), size);
    }

    static void setImageSize(@NonNull Context context, @NonNull ImageSize size) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(IMAGE_SIZE, size.name());
        editor.apply();
    }

    static void setImageQuality(@NonNull Context context, int quality) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(IMAGE_QUALITY, quality);
        editor.apply();
    }

    @NonNull
    static ImageSize getImageSize(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String imageSize = sharedPref.getString(IMAGE_SIZE, null);
        try {
            return (imageSize == null) ? ImageSize.Maximum : ImageSize.valueOf(imageSize);
        } catch (IllegalArgumentException e) {
            return ImageSize.Maximum;
        }
    }

    static int getImageQuality(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return sharedPref.getInt(IMAGE_QUALITY, 95);
    }
}
