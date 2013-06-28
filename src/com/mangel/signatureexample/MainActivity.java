package com.mangel.signatureexample;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private SignaturePadView mSignatureView;
	private TextView mText;
	private ImageView mImage;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mSignatureView = (SignaturePadView) findViewById(R.id.signatureView1);
        mText = (TextView) findViewById(R.id.textView1);
        mImage = (ImageView) findViewById(R.id.imageView);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.action_clear:
    		mSignatureView.clear();
    		return true;
    	case R.id.action_hasBeenMarked:
    		mText.setText(mSignatureView.hasBeenMarked() ? "Marked" : "Not marked");
    		return true;
    	case R.id.action_getImage:
    		mImage.setImageBitmap(mSignatureView.getImage());
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
}
