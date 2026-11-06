package app.nettoyage;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** Réglages et code PIN (stocké sous forme de hachage salé). */
class Store {
    private final SharedPreferences p;

    Store(Context c) {
        p = c.getSharedPreferences("nettoyage", Context.MODE_PRIVATE);
    }

    String lang() { return p.getString("lang", null); }
    void setLang(String l) { p.edit().putString("lang", l).apply(); }

    boolean useTrash() { return p.getBoolean("trash", true); }
    void setUseTrash(boolean b) { p.edit().putBoolean("trash", b).apply(); }

    int purgeDays() { return p.getInt("purge", 30); }
    void setPurgeDays(int d) { p.edit().putInt("purge", d).apply(); }

    boolean hasPin() { return p.getString("pin_hash", null) != null; }

    void setPin(String pin) {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        String salt = hex(s);
        p.edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply();
    }

    void clearPin() { p.edit().remove("pin_salt").remove("pin_hash").apply(); }

    boolean checkPin(String pin) {
        String salt = p.getString("pin_salt", "");
        String h = p.getString("pin_hash", null);
        if (h == null) return true;
        return MessageDigest.isEqual(hash(salt, pin).getBytes(), h.getBytes());
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = (salt + pin).getBytes("UTF-8");
            for (int i = 0; i < 2000; i++) d = md.digest(d);
            return hex(d);
        } catch (Exception e) {
            return "";
        }
    }
}
