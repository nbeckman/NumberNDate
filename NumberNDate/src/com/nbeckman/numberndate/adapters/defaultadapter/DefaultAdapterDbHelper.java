package com.nbeckman.numberndate.adapters.defaultadapter;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

// A helper for creating an deleting all of the tables in a default
// adapter database.
public final class DefaultAdapterDbHelper extends SQLiteOpenHelper {
	// Increment this every time the DB changes.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "DefaultBudgetAdapter.db";
	
	public DefaultAdapterDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DefaultPendingExpensesContract.SQL_CREATE_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
		// Delete these pending numbers. Ouch.
		// TODO(nbeckman): Preserve through the upgrade?
		db.execSQL(DefaultPendingExpensesContract.SQL_DELETE_TABLE);
        onCreate(db);
	}
	@Override
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
