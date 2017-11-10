package com.woc.chat.activity;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.support.v7.app.AppCompatActivity;
import android.webkit.*;
import android.view.View.*;
import android.view.GestureDetector.*;
import android.view.*;
import android.widget.*;

import com.woc.chat.MainActivity;
import com.woc.chat.R;
import com.woc.chat.util.IO;
import com.woc.chat.util.StatusBar;
import com.woc.chat.view.MdWebView;

public class PreviewMdAct extends AppCompatActivity
{
	private  MdWebView wv;
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		
		// TODO: Implement this method
		super.onCreate(savedInstanceState);
		setContentView(R.layout.preview_compose);
		wv=(MdWebView)findViewById(R.id.previewmdWebView1);
		
		Intent intent=getIntent();
		String data=intent.getStringExtra("data");
		String title=intent.getStringExtra("title");
		setTitle(title);
		StringBuilder sb=new StringBuilder();
		sb.append("<html>\n<head>\n\n");
		sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n");
		//sb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"markdown.css\">\n");
		sb.append("<style type=\"text/css\">\n");
		sb.append(IO.getFromAssets(this, "markdown.css"));
		sb.append("</style>");
		sb.append("</head>\n<body>\n");
		sb.append(IO.md2html(data));
		sb.append("\n</body>\n");
		sb.append("</html>");
		wv.loadData(sb.toString());

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		//沉浸状态栏
		StatusBar.setColor(this, Color.parseColor("#303F9F"));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
				/*
				 * 将actionBar的HomeButtonEnabled设为ture，
				 * 
				 * 将会执行此case
				 */
			case android.R.id.home:
				finish();
				break;
			
		}
		return super.onOptionsItemSelected(item);
	}

}
