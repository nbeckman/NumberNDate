package com.nbeckman.numberndate;

import com.nbeckman.numberndate.R;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

// A wrapper for the settings fragment that actually contains the settings.
// Not sure why fragments are good actually...
public class NumberNDateSettingsActivity extends Activity {	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new NumberNDateSettingsFragment())
                .commit();
    }
	
	// This 'fragment' is a tiny view that can be displayed by a full
	// activity. This one lets the user choose preferences.
	public static class NumberNDateSettingsFragment extends PreferenceFragment 
												   implements OnSharedPreferenceChangeListener {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.numbersndate_preferences);
			
	        // Set summaries to their values in code.
			final String account = AccountManager.getStoredAccount(getActivity());
			// TODO This is returning null...
			final Preference account_pref = findPreference(AccountManager.kAccountPreferencesName);
			account_pref.setSummary(account);

			final SharedPreferences shared_pref = 
					PreferenceManager.getDefaultSharedPreferences(getActivity());
	        final String spreadsheet = 
	        		shared_pref.getString(SpreadsheetFileManager.kNumbersSpreadsheetPreferencesName, "");
			final Preference accountPref = 
					findPreference(SpreadsheetFileManager.kNumbersSpreadsheetPreferencesName);
	        accountPref.setSummary(spreadsheet);
	        
	        // This used to be in onResume(), but I found it was unregistering when I went to
	        // other activities to change the value.
	        shared_pref.registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			// When a preference changes, load its new value as the summary. 
			SharedPreferences shared_pref = 
					PreferenceManager.getDefaultSharedPreferences(getActivity());
			final String value = shared_pref.getString(key, "");
			final Preference pref = findPreference(key);
			if (pref != null) {
				// Not all keys in the shared preferences store have a corresponding
				// Preference view.
				pref.setSummary(value);
			}
		}
		
		@Override
		public void onResume() {
		    super.onResume();
		}

		@Override
		public void onPause() {
		    super.onPause();
		    // TODO(nbeckman): All of the examples says to register/unregister as
		    // a preference change listener in onResume/onPause, but if I do that
		    // I never get the callback when the preference is changed in the
		    // AccountManager activity. So instead, I moved it to onCreate, and life
		    // is good.
		    // Try to put it back here if we have leaks and stuff.
		}
	}

}