package com.nbeckman.numberndate.adapters.defaultadapter;

import java.io.IOException;
import java.net.MalformedURLException;
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
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;
import com.nbeckman.numberndate.adapters.SpreadsheetAdapter;
import com.nbeckman.numberndate.adapters.defaultadapter.DefaultPendingNumbersContract.PendingNumbersEntry;

// Default spreadsheet adapter. Stores numbers in the Sql database until they
// are posted to the spreadsheet, which occurs by adding a new row.
public class DefaultSpreadsheetAdapter implements SpreadsheetAdapter {
	private final DefaultAdapterDbHelper dbHelper;
	private final WorksheetEntry worksheetFeed;
	private final SpreadsheetService spreadsheetService;
	
	public DefaultSpreadsheetAdapter(
			Context context,
			WorksheetEntry feed, 
			SpreadsheetService spreadsheetService) {
		this.dbHelper = new DefaultAdapterDbHelper(context);
		this.worksheetFeed = feed;
		this.spreadsheetService = spreadsheetService;
		
		// Uncomment to clear database for testing.
//		// TODO
//		final SQLiteDatabase db = this.dbHelper.getWritableDatabase();
//		db.delete(ExpenseEntry.TABLE_NAME, "1", null);
	}
	
	@Override
	public void AddValue(double number) {
		final long date_added = System.currentTimeMillis();
		
		// Create a new database entry for this number. The DB handler will post it later.
		SQLiteDatabase db = this.dbHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(PendingNumbersEntry.COLUMN_NAME_DATE_ADDED, date_added);
		values.put(PendingNumbersEntry.COLUMN_NAME_NUMBER, number);
		db.insert(PendingNumbersEntry.TABLE_NAME, null, values);
	}

	@Override
	public long NumOutstandingEntries() {
		final SQLiteDatabase db = this.dbHelper.getReadableDatabase();
		return DatabaseUtils.queryNumEntries(db, PendingNumbersEntry.TABLE_NAME);
	}
	
	@Override
	public boolean PostOneEntry() {
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
		try {
			// Fetch the list feed of the worksheet.
		    URL listFeedUrl = worksheetFeed.getListFeedUrl();
		    ListFeed listFeed = spreadsheetService.getFeed(listFeedUrl, ListFeed.class);
		    
		    // Convert date in millis to DD/MM/YY
		    DateFormat date_format = SimpleDateFormat.getDateInstance();
	        Date date_added = new Date(date_added_millis);
		    
		    // Create a local representation of the new row. This is truly horrible;
	        // you cannot add a new row the the spreadsheet except by naming the column
	        // header that it should go in.
	        // TODO(nbeckman): Make sure the columns have these names.
		    ListEntry row = new ListEntry();
		    row.getCustomElements().setValueLocal("date", date_format.format(date_added));
		    row.getCustomElements().setValueLocal("number", Double.toString(number));

		    Log.i("DefaultSpreadsheetAdapter", "Attempting to post number " + number +
		    		" and date " + date_format.format(date_added) + " to url " + listFeedUrl);
		    
		    // Send the new row to the API for insertion.
		    row = listFeed.insert(row);
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
		}
		return false;
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
