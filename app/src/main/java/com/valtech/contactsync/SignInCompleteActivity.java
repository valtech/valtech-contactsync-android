package com.valtech.contactsync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class SignInCompleteActivity extends Activity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sign_in_complete);

    Intent intent = getIntent();
    TextView header = (TextView)findViewById(R.id.header);
    TextView info = (TextView)findViewById(R.id.info);

    if (intent.getBooleanExtra("error", true)) {
      header.setText(getString(R.string.sign_in_completed_error_header));
      info.setText(getString(R.string.sign_in_completed_error_text));
    } else {
      header.setText(getString(R.string.sign_in_completed_success_header));
      info.setText(String.format(getString(R.string.sign_in_completed_success_text), intent.getStringExtra("email")));
    }
  }
}
