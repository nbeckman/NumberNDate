package com.nbeckman.numberndate;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.ServiceException;
import com.nbeckman.numberndate.adapters.BudgetCategory;
import com.nbeckman.numberndate.adapters.BudgetMonth;
import com.nbeckman.numberndate.adapters.SpreadsheetAdapter;
import com.nbeckman.numberndate.adapters.defaultadapter.DefaultSpreadsheetAdapter;

// Main activity for the Number N' Date app. Must set up the display
// and send people off to select a user and spreadsheet if need be.
public class MainNumbersActivity extends Activity {
	
	// I'm anticipating having several callbacks for checking/posting
	// the latest spreadsheet information. Here is the first one:
	private OutstandingExpensesPoster expenses_poster_ = null;
	
	private SpreadsheetAdapter budget_adapter_ = null;
	private SpreadsheetService spreadsheet_service_ = null;
	private WorksheetFeed worksheet_feed_ = null;
	
	private CellEntry month_total_cell_ = null;
	
	// Currently selected cells.
	private BudgetMonth selected_month_cell_ = null;
	private BudgetCategory selected_category_cell_ = null;
	
	// The listener for the 'add expense' button.
	// TODO(nbeckman): Probably make this its own class.
	private OnClickListener add_expense_button_listener_ = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			// If there's no selected category/month, we do nothing.
			if (selected_category_cell_ == null || selected_month_cell_ == null ||
					budget_adapter_ == null) {
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
			expense_textbox.setEnabled(false);
			
			final double final_expense_amount = expense_amount;
			(new AsyncTask<String, String, String>(){
				@Override
				protected String doInBackground(String... params) {
					budget_adapter_.AddValue(final_expense_amount);
					return "";
				}
				@Override
				protected void onPostExecute(String result) {
					if (expenses_poster_ != null) {
						expenses_poster_.forceUpdateUI();
					}
					// Reenable text box again.
					expense_textbox.setEnabled(true);
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

        final Button add_expense_button = (Button)findViewById(R.id.add_expense_button);
        add_expense_button.setOnClickListener(add_expense_button_listener_);
        
        // Show a process dialog since loading the categories and
        // stuff can be slow.
        // TODO(nbeckman): This thing is like completely modal, so do something
        // better...
        final ProgressDialog progress_dialog = new ProgressDialog(this);
        progress_dialog.setTitle(getResources().getString(R.string.loading_spreadsheet));
        progress_dialog.setMessage(getResources().getString(R.string.wait_while_loading_spreadsheet));
        progress_dialog.show();
        
        // Make sure we are logged in, and have a spreadsheet chosen.
        // TODO: Just want to try months, this will be hacked up.
        // Like, do I really want this async task here?
        (new AsyncTask<String, String, InterfaceUpdateData>(){
        	@Override
        	protected InterfaceUpdateData doInBackground(String... arg0) {
        		final String account = AccountManager.getStoredAccount(MainNumbersActivity.this);
        		
        		Log.i("MainNumbersActivity", "Accound manager has stored account: " + account);
        		
        		// TODO XXX We have no account selected. Force an account picker if account.empty().
        		
    			// Can't be called in UI thread.
    			try {
    				spreadsheet_service_ = 
    						SpreadsheetUtils.setupSpreadsheetServiceInThisThread(MainNumbersActivity.this, account);
        			final String spreadsheet_url = ChooseFileActivity.getStoredSpreadsheetURL(MainNumbersActivity.this);
        			// Must be done in background thread.
        			worksheet_feed_ = spreadsheet_service_.getFeed(
        					new URL(spreadsheet_url), WorksheetFeed.class);
            		List<WorksheetEntry> worksheets = worksheet_feed_.getEntries();
            		WorksheetEntry worksheet = worksheets.get(0);
            		budget_adapter_ = new DefaultSpreadsheetAdapter(getBaseContext(), worksheet, spreadsheet_service_);
            		
            		// Start callback thread that will periodically try to post outstanding expenses.
            		final TextView outstanding_expenses = (TextView)findViewById(R.id.expensesToPostValue);
            		expenses_poster_ = new OutstandingExpensesPoster(
            			outstanding_expenses, 
            			budget_adapter_);
            		expenses_poster_.start();
            		
            		final long num_outstanding_expenses =
            			budget_adapter_.NumOutstandingEntries();
        			return new InterfaceUpdateData(num_outstanding_expenses);
				} catch (GoogleAuthException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ServiceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    			return null;
        	}
        	
        	@Override
        	protected void onPostExecute(InterfaceUpdateData update) {
                // Dismiss progress dialog.
                progress_dialog.dismiss();
        		if (update == null) {
        			return;
        		}
        		
        		// END PART I KNOW IS HACKED UP
        		if (update.getNumOutstandingExpenses() != null) {
        			final TextView outstanding_expenses_text_view =
        				(TextView)findViewById(R.id.expensesToPostValue);
        			outstanding_expenses_text_view.setText(
        				update.getNumOutstandingExpenses().toString());
        		}
        		
        		// Write total to total text box.
        		updateMonthTotalTextFromCell();
        	}}).execute();
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

	// If month_total_cell_ has a value, sets it directly as the total textbox's
	// value. Accordingly, this must be called from a UI thread.
	//
	// I think the cell data is immediately out of date after you update the
	// sheet? So we need to figure out what to do...
	private void updateMonthTotalTextFromCell() {
		// TODO(nbeckman): I need a better way of storing, what is the current month..
		if (month_total_cell_ != null) {
			// Initially, the total of the first month is displayed.
			// If another month is chosen, we can display that total.
			final String month_total = month_total_cell_.getCell().getValue();
			final TextView total_textbox = (TextView)findViewById(R.id.monthTotalDisplay);
			total_textbox.setText(month_total);
		}
	}
	
	
}
