package io.jenkinsci.security;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

class Utils {
    static boolean isJSONValid(String test) {
        if (test == null) return false;
        try {
            JSONObject.fromObject(test);
        } catch (JSONException ex) {
            try {
                JSONArray.fromObject(test);
            } catch (JSONException ex1) {
                return false;
            }
        }

        return true;
    }
}
