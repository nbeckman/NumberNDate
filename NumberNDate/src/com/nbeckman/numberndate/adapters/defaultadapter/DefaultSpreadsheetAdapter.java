package com.nbeckman.numberndate.adapters.defaultadapter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.ServiceException;
import com.nbeckman.numberndate.adapters.SpreadsheetAdapter;
import com.nbeckman.numberndate.adapters.defaultadapter.DefaultPendingNumbersContract.PendingNumbersEntry;

// Default spreadsheet adapter. Stores numbers in the database until they
// are posted to the spreadsheet, which occurs by adding a new row.
// TODO(nbeckman): Consider making this two classes; one that adds the expenses
// to the database, and the other that actually posts them when the network is
// ready.
public class DefaultSpreadsheetAdapter implements SpreadsheetAdapter {
	private static final String NUMBER_HEADER_STRING = "number";
	private static final String DATE_HEADER_STRING = "date";
	
	private final DefaultAdapterDbHelper dbHelper;
	// The Url of the entire spreadsheet feed.
	private final String spreadsheetFeedUrl;
	// Authenticated spreadsheet service used for making calls out to Google Docs.
	// This field can be changed as we expect it to be set only once a connection
	// to the network has been established.
	// MUST BE GUARDED BY this, AS IT CAN CHANGE.
	private SpreadsheetService spreadsheetService = null;
	
	public DefaultSpreadsheetAdapter(
			Context context, String spreadsheetFeedUrl) {
		this.dbHelper = new DefaultAdapterDbHelper(context);
		this.spreadsheetFeedUrl = spreadsheetFeedUrl;
		
		// Uncomment to clear database for testing.
//		// TODO
//		final SQLiteDatabase db = this.dbHelper.getWritableDatabase();
//		db.delete(ExpenseEntry.TABLE_NAME, "1", null);
	}
	
	public synchronized void setSpreadsheetService(SpreadsheetService spreadsheetService) {
		this.spreadsheetService = spreadsheetService;
	}
	
	@Override
	public void AddValue(double number) {
		final long date_added = System.currentTimeMillis();
		
		// Create a new database entry for this number. The DB handler will post it later.
		SQLiteDatabase db = this.dbHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(PendingNumbersEntry.COLUMN_NAME_DATE_ADDED, date_added);
		values.put(PendingNumbersEntry.COLUMN_NAME_NUMBER, number);
		values.put(PendingNumbersEntry.COLUMN_NAME_SPREADSHEET_FEED_URL, spreadsheetFeedUrl);
		db.insert(PendingNumbersEntry.TABLE_NAME, null, values);
	}

	@Override
	public long NumOutstandingEntries() {
		final SQLiteDatabase db = this.dbHelper.getReadableDatabase();
		return DatabaseUtils.queryNumEntries(db, PendingNumbersEntry.TABLE_NAME);
	}
	
	@Override
	public synchronized boolean PostOneEntry() {
		// The service can be null until a connection to the network
		// is established.
		if (this.spreadsheetService == null) {
			return false;
		}
		
		// Get the oldest expense by ID if there is one, and then
		// post it.
		final SQLiteDatabase db = this.dbHelper.getWritableDatabase();
		// Projection: Until it matters, just return all the columns.
		final String[] projection = null;
		final String sortOrder =
				PendingNumbersEntry.COLUMN_NAME_DATE_ADDED + " ASC";
		final Cursor cursor = db.query(
				PendingNumbersEntry.TABLE_NAME, 
			    projection,                              
			    "",                 
			    new String[0],                        
			    null,                           
			    null,                           
			    sortOrder,             
			    "1");  // Return just the oldest row.
		if (!cursor.moveToFirst() || cursor.getCount() == 0) {
			return false;
		}
		final double number =
			cursor.getDouble(
				cursor.getColumnIndexOrThrow(
					PendingNumbersEntry.COLUMN_NAME_NUMBER));
		final long date_added_millis =
				cursor.getLong(
					cursor.getColumnIndexOrThrow(
						PendingNumbersEntry.COLUMN_NAME_DATE_ADDED));
		final String spreadsheet_url = 
				cursor.getString(
					cursor.getColumnIndexOrThrow(
						PendingNumbersEntry.COLUMN_NAME_SPREADSHEET_FEED_URL));
		try {
			// Fetch the list feed of the first worksheet.
			WorksheetFeed worksheed_feed = spreadsheetService.getFeed(new URL(spreadsheet_url), WorksheetFeed.class);
    		List<WorksheetEntry> worksheets = worksheed_feed.getEntries();
    		WorksheetEntry worksheet = worksheets.get(0);
		    URL listFeedUrl = worksheet.getListFeedUrl();
		    ListFeed listFeed = spreadsheetService.getFeed(listFeedUrl, ListFeed.class);
		    
		    // Convert date in millis to date in local format.
		    DateFormat date_format = SimpleDateFormat.getDateInstance();
	        Date date_added = new Date(date_added_millis);
		    
		    // Create a local representation of the new row. This is truly horrible;
	        // you cannot add a new row the the spreadsheet except by naming the column
	        // header that it should go in.
	        // TODO(nbeckman): Make sure the columns have these names.
		    ListEntry row = new ListEntry();
		    row.getCustomElements().setValueLocal(DATE_HEADER_STRING, date_format.format(date_added));
		    row.getCustomElements().setValueLocal(NUMBER_HEADER_STRING, Double.toString(number));

		    Log.i("DefaultSpreadsheetAdapter", "Attempting to post number " + number +
		    		" and date " + date_format.format(date_added) + " to url " + listFeedUrl);
		    
		    // Send the new row to the API for insertion.
		    try {
		    	row = listFeed.insert(row);
		    }
		    catch (com.google.gdata.util.InvalidEntryException e) {
		    	// This means in all likelihood, you don't have 'date' and 'number'
		    	// headers. Try to create them if we can, and insert again.
		    	maybeAddSpreadsheetHeaders(worksheet);
		    	// Just try again so at least the exception will be thrown...
		    	row = listFeed.insert(row);
		    }
			// Now delete that row so we don't process it again.
			// Technically we've got no atomicity here... Hopefully
			// the network operation is much more likely to fail than
			// this.
		    final long row_id = cursor.getLong(
		    		cursor.getColumnIndexOrThrow(PendingNumbersEntry._ID));
			// Define 'where' part of query.
			String selection = PendingNumbersEntry._ID + " LIKE ?";
			// Specify arguments in placeholder order.
			String[] selectionArgs = { String.valueOf(row_id) };
			// Issue SQL statement.
			db.delete(PendingNumbersEntry.TABLE_NAME, selection, selectionArgs);
		    return true;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ServiceException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	// If the header row is empty, add 'number' and 'date' headers, otherwise
	// we are in danger of messing up someone's data.
	private void maybeAddSpreadsheetHeaders(WorksheetEntry worksheet) throws URISyntaxException, IOException, ServiceException {
		// Fetch entire top row to see if it's empty.
		URL cellFeedUrl = new URI(worksheet.getCellFeedUrl().toString()
				+ "?min-row=1&max-row=1").toURL();
		CellFeed cellFeed = spreadsheetService.getFeed(cellFeedUrl, CellFeed.class);
		if (cellFeed.getEntries().isEmpty()) {
			// Now request two rows, and force them to be queried even if they are empty.
			cellFeedUrl = new URI(worksheet.getCellFeedUrl().toString()
					+ "?min-row=1&max-row=1&min-col=1&max-col=2&return-empty=true").toURL();
			cellFeed = spreadsheetService.getFeed(cellFeedUrl, CellFeed.class);
			// Set their values to number and date.
			CellEntry first_cell = cellFeed.getEntries().get(0);
			first_cell.changeInputValueLocal(DATE_HEADER_STRING);
			first_cell.update();
			
			CellEntry second_cell = cellFeed.getEntries().get(1);
			second_cell.changeInputValueLocal(NUMBER_HEADER_STRING);
			second_cell.update();
		} else {
			Log.w("DefaultSpreadsheetAdapter", "Can't add headers because spreadsheet is not empty.");
		}
	}

	private List<Loader<?>> month_oberservers_ = new ArrayList<Loader<?>>();
	@Override
	public void AddMonthsObserver(Loader<?> observer) {
		month_oberservers_.add(observer);
	}
	@Override
	public void RemoveMonthsObserver(Loader<?> observer) {
		month_oberservers_.remove(observer);
	}
	// TODO: Lots
	// have a start() method that will start a periodic (?) thread
	// it will load from the network
	// when network load is done, call 'detect changes & update'
	// after updates, call notifyMonthObservers()
	
}
