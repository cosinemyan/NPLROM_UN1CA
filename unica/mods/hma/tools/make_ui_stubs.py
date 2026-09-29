# SPDX-License-Identifier: AGPL-3.0-or-later
from pathlib import Path
import sys
root=Path(sys.argv[1])/'stubs'
stubs={
'androidx/fragment/app/FragmentActivity.java':'package androidx.fragment.app; public class FragmentActivity extends android.app.Activity {}',
'androidx/fragment/app/Fragment.java':'package androidx.fragment.app; public class Fragment { public FragmentActivity getActivity(){return null;} public boolean isAdded(){return false;} public void onCreate(android.os.Bundle b){} public void startActivityForResult(android.content.Intent i,int r){} public void onActivityResult(int r,int c,android.content.Intent d){} }',
'androidx/preference/Preference.java':'package androidx.preference; public class Preference { public interface OnPreferenceClickListener { boolean onPreferenceClick(Preference p); } public String getKey(){return null;} public void setSummary(CharSequence s){} public void setOnPreferenceClickListener(OnPreferenceClickListener l){} }',
'androidx/preference/TwoStatePreference.java':'package androidx.preference; public class TwoStatePreference extends Preference { public void setChecked(boolean b){} public boolean isChecked(){return false;} }',
'androidx/preference/PreferenceFragmentCompat.java':'package androidx.preference; public class PreferenceFragmentCompat extends androidx.fragment.app.Fragment { public void addPreferencesFromResource(int i){} public Preference findPreference(CharSequence k){return null;} }',
'com/android/settings/SettingsPreferenceFragment.java':'package com.android.settings; public class SettingsPreferenceFragment extends androidx.preference.PreferenceFragmentCompat {}',
'com/android/settings/search/BaseSearchIndexProvider.java':'package com.android.settings.search; public class BaseSearchIndexProvider { public BaseSearchIndexProvider(int i){} }',
'io/mesalabs/unica/utils/Utils.java':'package io.mesalabs.unica.utils; public class Utils { public static int getResourceId(String t,String n){return 0;} }',
}
for name,src in stubs.items():
 p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(src+'\n')
