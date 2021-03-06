/*
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.aicp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.net.TrafficStats;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SeekBarPreferenceCham;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

public class NetworkTrafficFragment extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String TAG = "NetworkTrafficVector";

    private static final String NETWORK_TRAFFIC_DISPLAY = "network_traffic_display";
    private static final String NETWORK_TRAFFIC_COLOR = "network_traffic_color";
    private static final String NETWORK_TRAFFIC_MONITOR = "network_traffic_monitor";
    private static final String NETWORK_TRAFFIC_PERIOD = "network_traffic_period";
    private static final String NETWORK_TRAFFIC_UNIT = "network_traffic_unit";
    private static final String NETWORK_TRAFFIC_AUTOHIDE = "network_traffic_autohide";
    private static final String NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD = "network_traffic_autohide_threshold";

    private ListPreference mNetTrafficDisplay;
    private ColorPickerPreference mNetTrafficColor;
    private ListPreference mNetTrafficMonitor;
    private ListPreference mNetTrafficPeriod;
    private ListPreference mNetTrafficUnit;
    private SwitchPreference mNetTrafficAutohide;
    private SeekBarPreferenceCham mNetTrafficAutohideThreshold;

    private static final int MENU_RESET = Menu.FIRST;
    private static final int DEFAULT_TRAFFIC_COLOR = 0xffffffff;

    private int mNetTrafficVal;
    private int MASK_METER;
    private int MASK_TEXT;
    private int MASK_UP;
    private int MASK_DOWN;
    private int MASK_PERIOD;
    private int MASK_UNIT;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.aicp_network_traffic_settings);

        loadResources();

        // Compute default state (meter with incoming traffic and 2s refresh period)
        int defaultState = 0;
        defaultState = setBit(defaultState, MASK_METER, true);
        defaultState = setBit(defaultState, MASK_DOWN, true);
        defaultState = setBit(defaultState, MASK_PERIOD, false) + (2000 << 16);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        mNetTrafficDisplay = (ListPreference) prefSet.findPreference(NETWORK_TRAFFIC_DISPLAY);
        mNetTrafficMonitor = (ListPreference) prefSet.findPreference(NETWORK_TRAFFIC_MONITOR);
        mNetTrafficPeriod = (ListPreference) prefSet.findPreference(NETWORK_TRAFFIC_PERIOD);
        mNetTrafficUnit = (ListPreference) prefSet.findPreference(NETWORK_TRAFFIC_UNIT);

        mNetTrafficColor =
            (ColorPickerPreference) prefSet.findPreference(NETWORK_TRAFFIC_COLOR);
        mNetTrafficColor.setOnPreferenceChangeListener(this);
        int intColor = Settings.System.getInt(getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_VECTOR_COLOR, 0xffffffff);
        String hexColor = String.format("#%08x", (0xffffffff & intColor));
            mNetTrafficColor.setSummary(hexColor);
            mNetTrafficColor.setNewPreviewColor(intColor);

        mNetTrafficAutohide =
                (SwitchPreference) prefSet.findPreference(NETWORK_TRAFFIC_AUTOHIDE);
        mNetTrafficAutohide.setChecked((Settings.System.getInt(getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE, 0) == 1));
        mNetTrafficAutohide.setOnPreferenceChangeListener(this);

        mNetTrafficAutohideThreshold =
                (SeekBarPreferenceCham) prefSet.findPreference(NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD);
        int netTrafficAutohideThreshold = Settings.System.getInt(getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE_THRESHOLD, 10);
        mNetTrafficAutohideThreshold.setValue(netTrafficAutohideThreshold);
        mNetTrafficAutohideThreshold.setOnPreferenceChangeListener(this);

        // TrafficStats will return UNSUPPORTED if the device does not support it.
        if (TrafficStats.getTotalTxBytes() != TrafficStats.UNSUPPORTED &&
                TrafficStats.getTotalRxBytes() != TrafficStats.UNSUPPORTED) {
            mNetTrafficVal = Settings.System.getInt(getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VECTOR_STATE, defaultState);
            int intIndex = mNetTrafficVal & (MASK_METER + MASK_TEXT);
            intIndex = mNetTrafficDisplay.findIndexOfValue(String.valueOf(intIndex));
            updateNetworkTrafficState(intIndex);

            mNetTrafficDisplay.setValueIndex(intIndex >= 0 ? intIndex : 0);
            mNetTrafficDisplay.setSummary(mNetTrafficDisplay.getEntry());
            mNetTrafficDisplay.setOnPreferenceChangeListener(this);

            intIndex = mNetTrafficVal & (MASK_UP + MASK_DOWN);
            intIndex = mNetTrafficMonitor.findIndexOfValue(String.valueOf(intIndex));
            mNetTrafficMonitor.setValueIndex(intIndex>=0? intIndex : 1);
            mNetTrafficMonitor.setSummary(mNetTrafficMonitor.getEntry());
            mNetTrafficMonitor.setOnPreferenceChangeListener(this);

            intIndex = (mNetTrafficVal & MASK_PERIOD) >>> 16;
            intIndex = mNetTrafficPeriod.findIndexOfValue(String.valueOf(intIndex));
            mNetTrafficPeriod.setValueIndex(intIndex>=0 ? intIndex : 3);
            mNetTrafficPeriod.setSummary(mNetTrafficPeriod.getEntry());
            mNetTrafficPeriod.setOnPreferenceChangeListener(this);

            mNetTrafficUnit.setValueIndex(getBit(mNetTrafficVal, MASK_UNIT) ? 1 : 0);
            mNetTrafficUnit.setSummary(mNetTrafficUnit.getEntry());
            mNetTrafficUnit.setOnPreferenceChangeListener(this);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.network_traffic_color_reset)
                .setIcon(R.drawable.ic_settings_reset)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                resetToDefault();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void resetToDefault() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(R.string.network_traffic_color_reset);
        alertDialog.setMessage(R.string.network_traffic_color_reset_message);
        alertDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                NetworkTrafficColorReset();
            }
        });
        alertDialog.setNegativeButton(R.string.cancel, null);
        alertDialog.create().show();
    }

    private void NetworkTrafficColorReset() {
        Settings.System.putInt(getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_VECTOR_COLOR, DEFAULT_TRAFFIC_COLOR);

        mNetTrafficColor.setNewPreviewColor(DEFAULT_TRAFFIC_COLOR);
        String hexColor = String.format("#%08x", (0xffffffff & DEFAULT_TRAFFIC_COLOR));
        mNetTrafficColor.setSummary(hexColor);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mNetTrafficDisplay) {
            int intState = Integer.valueOf((String) newValue);
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_METER, getBit(intState, MASK_METER));
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_TEXT, getBit(intState, MASK_TEXT));
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VECTOR_STATE, mNetTrafficVal);
            int index = mNetTrafficDisplay.findIndexOfValue((String) newValue);
            mNetTrafficDisplay.setSummary(mNetTrafficDisplay.getEntries()[index]);
            updateNetworkTrafficState(index);
            return true;
        } else if (preference == mNetTrafficColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VECTOR_COLOR, intHex);
            return true;  
        } else if (preference == mNetTrafficMonitor) {
            int intState = Integer.valueOf((String)newValue);
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_UP, getBit(intState, MASK_UP));
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_DOWN, getBit(intState, MASK_DOWN));
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VECTOR_STATE, mNetTrafficVal);
            int index = mNetTrafficMonitor.findIndexOfValue((String) newValue);
            mNetTrafficMonitor.setSummary(mNetTrafficMonitor.getEntries()[index]);
            return true;
        } else if (preference == mNetTrafficPeriod) {
            int intState = Integer.valueOf((String)newValue);
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_PERIOD, false) + (intState << 16);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VECTOR_STATE, mNetTrafficVal);
            int index = mNetTrafficPeriod.findIndexOfValue((String) newValue);
            mNetTrafficPeriod.setSummary(mNetTrafficPeriod.getEntries()[index]);
            return true;
        } else if (preference == mNetTrafficUnit) {
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_UNIT, ((String)newValue).equals("1"));
            Settings.System.putInt(getActivity().getContentResolver(),
            Settings.System.NETWORK_TRAFFIC_VECTOR_STATE, mNetTrafficVal);
            int index = mNetTrafficUnit.findIndexOfValue((String) newValue);
            mNetTrafficUnit.setSummary(mNetTrafficUnit.getEntries()[index]);
            return true;
        } else if (preference == mNetTrafficAutohide) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE, value ? 1 : 0);
            return true;
        } else if (preference == mNetTrafficAutohideThreshold) {
            int threshold = (Integer) newValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE_THRESHOLD, threshold);
            return true;
        }
        return false;
    }

    private void loadResources() {
        Resources resources = getActivity().getResources();
        MASK_METER = resources.getInteger(R.integer.maskMeter);
        MASK_TEXT = resources.getInteger(R.integer.maskText);
        MASK_UP = resources.getInteger(R.integer.maskUp);
        MASK_DOWN = resources.getInteger(R.integer.maskDown);
        MASK_UNIT = resources.getInteger(R.integer.maskUnit);
        MASK_PERIOD = resources.getInteger(R.integer.maskPeriod);
    }

    private void updateNetworkTrafficState(int mIndex) {
        // Check display setting
        if (mIndex <= 0) {
            // Disable all settings
            mNetTrafficMonitor.setEnabled(false);
            mNetTrafficColor.setEnabled(false);
            mNetTrafficPeriod.setEnabled(false);
            mNetTrafficUnit.setEnabled(false);
            mNetTrafficAutohide.setEnabled(false);
            mNetTrafficAutohideThreshold.setEnabled(false);
        } else {
            // Enable common settings
            mNetTrafficMonitor.setEnabled(true);
            mNetTrafficPeriod.setEnabled(true);
            // Check meter display
            if (mIndex == 1) {
                // Disable unsupported settings by meters
                mNetTrafficColor.setEnabled(false);
                mNetTrafficUnit.setEnabled(false);
                mNetTrafficAutohide.setEnabled(false);
                mNetTrafficAutohideThreshold.setEnabled(false);
            } else if (mIndex == 2) {
                mNetTrafficColor.setEnabled(true);
                mNetTrafficUnit.setEnabled(true);
                mNetTrafficAutohide.setEnabled(true);
                mNetTrafficAutohideThreshold.setEnabled(true);
            } else {
                // Enable all other settings
                mNetTrafficColor.setEnabled(true);
                mNetTrafficUnit.setEnabled(true);
                mNetTrafficAutohide.setEnabled(true);
                mNetTrafficAutohideThreshold.setEnabled(true);
            }
        }
    }

    private int setBit(int intNumber, int intMask, boolean blnState) {
        if (blnState) {
            return (intNumber | intMask);
        }
        return (intNumber & ~intMask);
    }

    private boolean getBit(int intNumber, int intMask) {
        return (intNumber & intMask) == intMask;
    }
}
