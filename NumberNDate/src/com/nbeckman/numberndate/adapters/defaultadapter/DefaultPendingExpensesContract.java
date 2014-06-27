package com.nbeckman.numberndate.adapters.defaultadapter;

import android.provider.BaseColumns;

// A 'contract' for the SQLLite database table that holds
// pending numbers. All numbers entered by the user go into
// this table until such time as they can be written back to the
// spreadsheet. The contract is just a convenient way
// of specifying the schema for a table.
//
// The idea for this comes from the Android dev docs:
// https://developer.android.com/training/basics/data-storage/databases.html
public final class DefaultPendingExpensesContract {
	private DefaultPendingExpensesContract() {}
	
	private static final String REAL_TYPE = " REAL";
	private static final String INTEGER_TYPE = " INTEGER";
	private static final String COMMA_SEP = ",";
	
	// How to create this table.
	public static final String SQL_CREATE_TABLE =
	    "CREATE TABLE " + ExpenseEntry.TABLE_NAME + " (" +
	    ExpenseEntry._ID + " INTEGER PRIMARY KEY," +
	    ExpenseEntry.COLUMN_NAME_NUMBER + REAL_TYPE + COMMA_SEP +
	    ExpenseEntry.COLUMN_NAME_DATE_ADDED + INTEGER_TYPE +
	    ");";
	
	// How to delete this table
	static final String SQL_DELETE_TABLE =
		"DROP TABLE IF EXISTS " + ExpenseEntry.TABLE_NAME;
	
	// Inner class that defines the table contents.
    public static abstract class ExpenseEntry implements BaseColumns {
		public static final String TABLE_NAME = "outstanding_expenses";
    	
    	// Each constant starting with COLUMN_NAME_ is a new column.
    	
    	// A 'real' value holding the number. 
    	public static final String COLUMN_NAME_NUMBER = "number";
    	// The date this value was added.
    	public static final String COLUMN_NAME_DATE_ADDED = "date_added";

    }
}