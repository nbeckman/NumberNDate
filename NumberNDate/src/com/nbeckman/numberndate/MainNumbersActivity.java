package com.nbeckman.numberndate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.nbeckman.numberndate.adapters.SpreadsheetAdapter;
import com.nbeckman.numberndate.adapters.defaultadapter.DefaultSpreadsheetAdapter;

// Main activity for the Number N' Date app. Must set up the display
// and send people off to select a user and spreadsheet if need be.
public class MainNumbersActivity extends Activity implements OnSharedPreferenceChangeListener {
	
	// Intent ID; this tells us when the account picker is returning. 
	private static final int kAccountChoiceIntent = 1;
	// Intent ID; this tells us when the SpreadsheetFileManager is returning.
	private static final int kSpreadsheetChoiceIntent = 2;

	// I'm anticipating having several callbacks for checking/posting
	// the latest spreadsheet information. Here is the first one:
	private OutstandingExpensesPoster expenses_poster_ = null;
	
	private SpreadsheetAdapter spreadsheet_adapter_ = null;
	private SpreadsheetService spreadsheet_service_ = null;
	
	// The listener for the 'add expense' button.
	private OnClickListener add_expense_button_listener_ = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			if (spreadsheet_adapter_ == null) {
				return;
			}
			final TextView expense_textbox = 
					(TextView)findViewById(R.id.expense_amount_textbox);
			double expense_amount = 0.0;
			try {
				expense_amount = 
						Double.parseDouble(expense_textbox.getText().toString());
			} catch(NumberFormatException e) {
				return;
			}
			// Text box cannot be edited while we are changing the value.
			disableControls();
			
			final double final_expense_amount = expense_amount;
			(new AsyncTask<String, String, String>(){
				@Override
				protected String doInBackground(String... params) {
					spreadsheet_adapter_.AddValue(final_expense_amount);
					return "";
				}
				@Override
				protected void onPostExecute(String result) {
					if (expenses_poster_ != null) {
						expenses_poster_.forceUpdateUI();
					}
					// Reenable text box again.
					enableControls();
					expense_textbox.setText("");
					expense_textbox.setHint(R.string.expense_amount_hint);
				}
			}).execute();
		}
	};
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_budget);

        // Everything is greyed out until we know we have a
        // spreadsheet chosen (and thus, hopefully, also an account).
        this.disableControls();
        
        final Button add_expense_button = (Button)findViewById(R.id.add_expense_button);
        add_expense_button.setOnClickListener(add_expense_button_listener_);
        
        if (establishAccountAndSpreadsheet()) {
        	startDisplayLogic();
        }
        
        // Add this activity as a preferences listener, so that when the spreadsheet
        // is changed we can be sure to re-run startDisplayLogic().
    	final SharedPreferences shared_pref = 
				PreferenceManager.getDefaultSharedPreferences(this);
    	shared_pref.registerOnSharedPreferenceChangeListener(this);
    }
    
    // Helper method to disable everything (important) in the view. This allows us to,
    // e.g., disable the controls until the user has chosen a spreadsheet.
    private void disableControls() {
    	final Button add_expense_button = (Button)findViewById(R.id.add_expense_button);
    	add_expense_button.setEnabled(false);
    	final TextView expense_acount_textbox = 
    			(TextView)findViewById(R.id.expense_amount_textbox);
    	expense_acount_textbox.setEnabled(false);
    }
    
    // Enables all the views disabled by disableControls.
    private void enableControls() {
    	final Button add_expense_button = (Button)findViewById(R.id.add_expense_button);
    	add_expense_button.setEnabled(true);
    	final TextView expense_acount_textbox = 
    			(TextView)findViewById(R.id.expense_amount_textbox);
    	expense_acount_textbox.setEnabled(true);
    }
    
    private boolean establishAccountAndSpreadsheet() {
    	if(AccountManager.hasStoredAccount(this)) {
    		if (SpreadsheetFileManager.hasStoredSpreadsheet(this)) {
    			// Now we can actually do something.
    			return true;
    		} else {
    			// We need to call the ChooseFileActivity from an intent.
    			Intent choose_spreadsheet_intent = new Intent(this, SpreadsheetFileManager.class);
        		startActivityForResult(choose_spreadsheet_intent, kSpreadsheetChoiceIntent);
    		}
    	} else {
    		// We need to force the account picker intent.
    		Intent choose_account_intent = AccountManager.newReturnAuthorizedUserIntent(this);
    		startActivityForResult(choose_account_intent, kAccountChoiceIntent);
    	}
    	return false;
    }
    
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == kAccountChoiceIntent && resultCode == RESULT_OK) {
			// Do it again, this time to get the spreadsheet.
			if (establishAccountAndSpreadsheet()) {
				startDisplayLogic();
			}
			// In the else case, we are waiting for the next spreadsheet choice result.
		} else if (requestCode == kSpreadsheetChoiceIntent && resultCode == RESULT_OK) {
			// We are ready to begin.
			// TODO(nbeckman): This call could actually be redundant as the preferences
			// listener should automatically call startDisplayLogic().
			startDisplayLogic();
		} else {
			Log.w("MainNumbersActivity", "Weird unhandled activity result. requestCode: " + requestCode
					+ " resultCode: " + resultCode);
		}
	}
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
		// Kill all of the rando background threads we may 
		// have running.
		if (this.expenses_poster_ != null) {
			this.expenses_poster_.stop();
		}
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	// Just the code to make my list of menu items come up.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.megabudget_menu, menu);
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Someone clicked on _something_ in the menu, and since
		// we have only one item, we assume it was the settings
		// menu. Launch it... 
		Intent intent = new Intent();
	    intent.setClass(this, NumberNDateSettingsActivity.class);
	    startActivity(intent);
		return true;
	}
	
	// Needs a better name; attempts to load a user and spreadsheet once these properties have
	// both already been selected by the user and stored in preferences.
	private void startDisplayLogic() {
		// By this point we should definitely have an account, so we can enable the controls
		// that allow the user to enter stuff.
		this.enableControls();
		
        // Make sure we are logged in, and have a spreadsheet chosen.
        (new AsyncTask<String, String, InterfaceUpdateData>(){
        	@Override
        	protected InterfaceUpdateData doInBackground(String... arg0) {
        		final String account = AccountManager.getStoredAccount(MainNumbersActivity.this);
        		Log.i("MainNumbersActivity", "Account manager has stored account: " + account);
        		
        		final String spreadsheet_url = SpreadsheetFileManager.getStoredSpreadsheetURL(MainNumbersActivity.this);
        		final DefaultSpreadsheetAdapter next_spreadsheet_adapter = 
        				 new DefaultSpreadsheetAdapter(getBaseContext(), spreadsheet_url);
        		spreadsheet_adapter_ = next_spreadsheet_adapter;
        		// Start callback thread that will periodically try to post outstanding expenses. If there had been
        		// one running before, stop the old one so it will no longer post to the old spreadsheet.
        		if (expenses_poster_ != null) {
        			expenses_poster_.stop();
        		}
        		final TextView outstanding_expenses = (TextView)findViewById(R.id.expensesToPostValue);
        		expenses_poster_ = 
        			new OutstandingExpensesPoster(outstanding_expenses, spreadsheet_adapter_);
        		expenses_poster_.start();
        		
        		final long num_outstanding_expenses =
        			spreadsheet_adapter_.NumOutstandingEntries();
        		final InterfaceUpdateData result = new InterfaceUpdateData(num_outstanding_expenses);
    			
    			try {
    				spreadsheet_service_ = 
    						SpreadsheetUtils.setupSpreadsheetServiceInThisThread(MainNumbersActivity.this, account);
        			next_spreadsheet_adapter.setSpreadsheetService(spreadsheet_service_);
				} catch (GoogleAuthException e) {
					// Not yet authenticated to use the spreadsheet.
					e.printStackTrace();
				} 
    			return result;
        	}
        	
        	@Override
        	protected void onPostExecute(InterfaceUpdateData update) {
        		if (update == null) {
        			return;
        		}
        		
        		if (update.getNumOutstandingExpenses() != null) {
        			final TextView outstanding_expenses_text_view =
        				(TextView)findViewById(R.id.expensesToPostValue);
        			outstanding_expenses_text_view.setText(
        				update.getNumOutstandingExpenses().toString());
        		}
        	}}).execute();
		// Since there's only one thing you want to do, type in a number,
		// start with the keyboard out and focused on the number box.
        // TODO(nbeckman): This does nothing and I can't get it to work.
		EditText editText = (EditText)findViewById(R.id.expense_amount_textbox);
		editText.requestFocus();
		editText.requestFocusFromTouch();
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences shared_prefs, String key) {
		if (key.equals(SpreadsheetFileManager.kNumbersSpreadsheetPreferencesName)) {
			// See if  we've changed the settings TO a spreadsheet, or if we have somehow
			// cleared it.
			if (SpreadsheetFileManager.getStoredSpreadsheet(this) == null ||
					SpreadsheetFileManager.getStoredSpreadsheet(this).length() == 0) {
				this.disableControls();
			} else {
				this.startDisplayLogic();
			}
		}
	}
}
