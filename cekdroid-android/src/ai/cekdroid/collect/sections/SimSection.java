package ai.cekdroid.collect.sections;

import android.content.Context;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * SIM slots, operator, and roaming.
 *
 * IMEI, MEID, IMSI, and ICCID are deliberately absent: since Android 10 they
 * require READ_PRIVILEGED_PHONE_STATE, which third-party apps cannot hold.
 * Those rows are written as null with the reason instead of being dropped, so
 * the UI does not look like it simply forgot them.
 */
public class SimSection implements Section {

    @Override
    public String id() {
        return "sim";
    }

    @Override
    public String title() {
        return "SIM";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        TelephonyManager tm =
                (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) {
            out.warn("TelephonyManager tidak tersedia");
            return;
        }

        try {
            out.put("sim_state", simStateName(tm.getSimState()));
            out.put("phone_type", phoneTypeName(tm.getPhoneType()));
            out.put("sim_operator_name", SectionBase.s(tm.getSimOperatorName()));
            out.put("sim_operator_code", SectionBase.s(tm.getSimOperator()));
            out.put("sim_country", SectionBase.s(tm.getSimCountryIso()));
            out.put("network_country", SectionBase.s(tm.getNetworkCountryIso()));
            out.put("roaming", tm.isNetworkRoaming());
            out.put("slot_count", tm.getPhoneCount());
        } catch (SecurityException e) {
            out.warn("info SIM butuh izin READ_PHONE_STATE");
        } catch (Throwable t) {
            out.warn("info SIM: " + t.getMessage());
        }

        // These are the rows users of a device-info app look for. Name the
        // platform limit explicitly instead of leaving a confusing blank.
        out.put("imei", null);
        out.put("meid", null);
        out.put("imsi", null);
        out.put("iccid", null);
        out.warn("IMEI/MEID/IMSI/ICCID tidak dapat dibaca app biasa (butuh READ_PRIVILEGED_PHONE_STATE sejak Android 10)");

        List<LinkedHashMap<String, Object>> slots = new ArrayList<>();
        try {
            SubscriptionManager sm = (SubscriptionManager) ctx.getSystemService(
                    Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> subs = sm == null ? null : sm.getActiveSubscriptionInfoList();
            if (subs != null) {
                for (SubscriptionInfo info : subs) {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("slot", info.getSimSlotIndex());
                    row.put("subscription_id", info.getSubscriptionId());
                    row.put("carrier", info.getCarrierName() == null
                            ? null : info.getCarrierName().toString());
                    row.put("country", info.getCountryIso());
                    row.put("display_name", info.getDisplayName() == null
                            ? null : info.getDisplayName().toString());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        row.put("mcc", info.getMccString());
                        row.put("mnc", info.getMncString());
                    }
                    slots.add(row);
                }
            } else {
                out.warn("daftar SIM aktif tidak terbaca (butuh izin telepon)");
            }
        } catch (SecurityException e) {
            out.warn("daftar SIM butuh izin READ_PHONE_STATE");
        } catch (Throwable t) {
            out.warn("daftar SIM: " + t.getMessage());
        }
        out.rows("slots", slots);
    }

    private static String simStateName(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_ABSENT: return "absent";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "pin_required";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "puk_required";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "network_locked";
            case TelephonyManager.SIM_STATE_READY: return "ready";
            case TelephonyManager.SIM_STATE_NOT_READY: return "not_ready";
            case TelephonyManager.SIM_STATE_PERM_DISABLED: return "perm_disabled";
            case TelephonyManager.SIM_STATE_CARD_IO_ERROR: return "card_io_error";
            case TelephonyManager.SIM_STATE_CARD_RESTRICTED: return "card_restricted";
            case TelephonyManager.SIM_STATE_UNKNOWN: return null;
            default: return "state_" + state;
        }
    }

    private static String phoneTypeName(int type) {
        switch (type) {
            case TelephonyManager.PHONE_TYPE_GSM: return "gsm";
            case TelephonyManager.PHONE_TYPE_CDMA: return "cdma";
            case TelephonyManager.PHONE_TYPE_SIP: return "sip";
            case TelephonyManager.PHONE_TYPE_NONE: return null;
            default: return "type_" + type;
        }
    }
}
