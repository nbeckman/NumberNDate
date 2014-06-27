package com.nbeckman.numberndate.adapters;

import android.content.Loader;

// Spreadsheet adapter is an interface, implemented by classes that
// interact directly with the spreadsheet by writing information to
// it as a feed. We probably will not need multiple implementations of
// the adapter. In theory there could be multiple spreadsheet formats,
// but in practice this is really just a remnant of Megabudget which
// I copied this all from.
public interface SpreadsheetAdapter {
	// Add the given number to the spreadsheet at the current time.
	public void AddValue(double number);
	
	// Post one stored expense to the spreadsheet. Returns true if one
	// expense was posted.
	//
	// BLOCKING:
	// This call is expected to contact the spreadsheet service and
	// possibly read from/write to the local database.
	public boolean PostOneExpense();
	
	// Return the number of expenses waiting to be posted.
	//
	// BLOCKING:
	// This call is expected to read from/write to the local database.
	public long NumOutstandingEntries();

	// Adds/removes a new observer. This class will call
	// onContentChanged on its observers when the months
	// database entries are changed.
	// 
	// TODO(nbeckman): Isn't there a better class than Loader?
	public void AddMonthsObserver(Loader<?> observer);
	public void RemoveMonthsObserver(Loader<?> observer);
}
