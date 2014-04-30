package com.soundsrc.creditcardsniffer;

import java.io.IOException;
import java.util.Arrays;

import com.soundsrc.creditcardsniffer.R;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {

	private NfcAdapter mAdapter;
	private TextView mAid;
	private TextView mLabel;
	private TextView mName;
	private TextView mCardNum;
	private TextView mExpire;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
		
		mAdapter = NfcAdapter.getDefaultAdapter(this);
		
		mAid = (TextView)findViewById(R.id.tvAid);
		mLabel = (TextView)findViewById(R.id.tvCardLabel);
		mName = (TextView)findViewById(R.id.tvName);
		mCardNum = (TextView)findViewById(R.id.tvCardNum);
		mExpire = (TextView)findViewById(R.id.tvExpire);
	}

	@Override
	public void onPause() {
	    super.onPause();
	    mAdapter.disableForegroundDispatch(this);
	}

	@Override
    public void onResume() {
        super.onResume();
        
        mAdapter.enableForegroundDispatch(this,
                PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0),
                new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED) },
                new String[][] { new String[] { IsoDep.class.getName() }});
     
        Intent intent = getIntent();
        // Check to see that the Activity started due to an Android Beam
        String action = intent.getAction();
        System.out.println(action);
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        System.out.println(tagFromIntent);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            processIntent(intent);
        }
    }
	
	@Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

	static byte[] stringToBytes(String s)
	{
		// copied from somewhere on stackoverflow
		int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	static String bytesToString(byte [] b)
	{
		String out = "";
	
		for(int i = 0; i < b.length; ++i) {
			out += String.format("%02X",b[i]);
		}

		return out;
	}
	
	static void printBytes(byte [] b)
	{
		for(int i = 0; i < b.length; ++i) {
			System.out.print(String.format("%02X ",b[i]));
		}
		System.out.println();
	}
	
	void processIntent(Intent intent) {
		Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        IsoDep isoDep = IsoDep.get(tag);
        if(isoDep != null) {
        	byte[] data;
        	try {
        		isoDep.connect();
        		
        		String aid = "";
        		byte[] payload;
        		
        		// Select PSE
        		payload = stringToBytes("00A404000E315041592E5359532E4444463031");
        		data = isoDep.transceive(payload);
        		//printBytes(data);
        		
        		payload = stringToBytes("00B2010C00");
        		data = isoDep.transceive(payload);
        		//printBytes(data);
        		
        		// returns 2 bytes indicating size
        		if(data[0] == 0x70) {
        			int len = data[1]; // total len
        			
        			//data[2] = 0x61
        			//data[3] = length of directory 1
        			
        			int i = 4;
        			while(i < len)
        			{
	        			int tagId = data[i++];
	        			int entryLen = data[i++];
	        			switch(tagId) {
	        				case 0x4F:
	        					aid = bytesToString(Arrays.copyOfRange(data, i, i + entryLen));
	        					mAid.setText("AID: " + aid);
	        					break;
	        				case 0x50:
	        					String label = new String(Arrays.copyOfRange(data, i, i + entryLen),"UTF-8");
	        					mLabel.setText("Card Label: " + label);
	        					break;
	        				default:
	        					break;
	        			}
	        			i += entryLen;
        			}
        		}

        		// select application
        		payload = stringToBytes("00A4040007" + aid);
        		data = isoDep.transceive(payload);
        		//printBytes(data);
        		
        		// read record
        		payload = stringToBytes("00B2010C00");
        		data = isoDep.transceive(payload);
        		//printBytes(data);

        		if(data[0] == 0x70) {
        			int recordLen = data[1];
        			int i = 2;
        			while(i < recordLen)
        			{
        				int tagId = data[i++];
        				int entryLen = data[i++];
        				
        				switch(tagId) {
        					case 0x57: // card number yo
        						byte [] expire = new byte[]{
        								(byte)((data[i+8] & 0xF) << 4 | (data[i+9] >> 4)),
        								(byte)((data[i+9] & 0xF) << 4 | (data[i+10] >> 4)) };
        						mCardNum.setText("Card #: " + bytesToString(Arrays.copyOfRange(data, i, i + 8)));
        						mExpire.setText("Expire: " + bytesToString(expire));
        						break;
        					case 0x5F: // your name
        						String name = new String(Arrays.copyOfRange(data, i, i + entryLen),"UTF-8");
        						mName.setText("Name: " + name);
        						break;
        				}
        				
        				i += entryLen;
        			}
        		}
        	} catch(IOException e) {
        	} finally { 
        		try { isoDep.close(); } catch(IOException e) {} 
        	}
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			return rootView;
		}
	}

}
