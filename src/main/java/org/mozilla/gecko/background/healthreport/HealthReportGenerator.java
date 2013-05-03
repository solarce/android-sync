/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.background.healthreport;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.mozilla.gecko.background.healthreport.HealthReportStorage.Field;

import android.database.Cursor;
import android.util.SparseArray;

public class HealthReportGenerator {
  private static final int PAYLOAD_VERSION = 3;

  private final HealthReportStorage storage;

  public HealthReportGenerator(HealthReportStorage storage) {
    this.storage = storage;
  }

  @SuppressWarnings("static-method")
  protected long now() {
    return System.currentTimeMillis();
  }

  /**
   * The document consists of:
   *
   * * Basic metadata: last ping time, current ping time, version.
   * * A map of environments: 'current' and others named by hash. 'current' is fully specified,
   *   and others are deltas from current.
   * * A 'data' object. This includes 'last' and 'days'.
   *   'days' is a map from date strings to {hash: {measurement: {_v: version, fields...}}}.
   */
  @SuppressWarnings("unchecked")
  public JSONObject generateDocument(long since, long lastPingTime, Environment currentEnvironment) {
    // We want to map field IDs to some strings as we go.
    SparseArray<Environment> envs = storage.getEnvironmentRecordsByID();

    JSONObject document = new JSONObject();
    if (lastPingTime >= HealthReportUtils.EARLIEST_LAST_PING) {
      document.put("lastPingDate", HealthReportUtils.getDateString(lastPingTime));
    }

    document.put("thisPingDate", HealthReportUtils.getDateString(now()));
    document.put("version", PAYLOAD_VERSION);

    document.put("environments", getEnvironmentsJSON(currentEnvironment, envs));
    document.put("data", getDataJSON(currentEnvironment, envs, since));

    return document;
  }

  @SuppressWarnings("unchecked")
  protected JSONObject getDataJSON(Environment currentEnvironment,
                                   SparseArray<Environment> envs, long since) {
    SparseArray<Field> fields = storage.getFieldsByID();

    JSONObject days = getDaysJSON(currentEnvironment, envs, fields, since);

    JSONObject last = new JSONObject();

    JSONObject data = new JSONObject();
    data.put("days", days);
    data.put("last", last);
    return data;
  }

  @SuppressWarnings("unchecked")
  protected JSONObject getDaysJSON(Environment currentEnvironment, SparseArray<Environment> envs, SparseArray<Field> fields, long since) {
    JSONObject days = new JSONObject();
    Cursor cursor = storage.getRawEventsSince(since);
    try {
      if (!cursor.moveToNext()) {
        return days;
      }

      // A classic walking partition.
      // Columns are "date", "env", "field", "value".
      // Note that we care about the type (integer, string) and kind
      // (last/counter, discrete) of each field.
      // Each field will be accessed once for each date/env pair, so
      // Field memoizes these facts.
      // We also care about which measurement contains each field.
      int lastDate  = -1;
      int lastEnv   = -1;
      JSONObject dateObject = null;
      JSONObject envObject = null;

      while (!cursor.isAfterLast()) {
        int cDate  = cursor.getInt(0);
        int cEnv   = cursor.getInt(1);
        int cField = cursor.getInt(2);

        boolean dateChanged = cDate != lastDate;
        boolean envChanged = cEnv != lastEnv;

        if (dateChanged) {
          if (dateObject != null) {
            days.put(HealthReportUtils.getDateStringForDay(lastDate), dateObject);
          }
          dateObject = new JSONObject();
          lastDate = cDate;
        }

        if (dateChanged || envChanged) {
          envObject = new JSONObject();
          dateObject.put(envs.get(cEnv).hash, envObject);
          lastEnv = cEnv;
        }

        final Field field = fields.get(cField);
        JSONObject measurement = (JSONObject) envObject.get(field.measurementName);
        if (measurement == null) {
          // We will never have more than one measurement version within a
          // single environment -- to do so involves changing the build ID. And
          // even if we did, we have no way to represent it. So just build the
          // output object once.
          measurement = new JSONObject();
          measurement.put("_v", field.measurementVersion);
          envObject.put(field.measurementName, measurement);
        }
        if (field.isDiscreteField()) {
          JSONArray discrete = (JSONArray) measurement.get(field.fieldName);
          if (discrete == null) {
            discrete = new JSONArray();
            measurement.put(field.fieldName, discrete);
          }
          if (field.isStringField()) {
            discrete.add(cursor.getString(3));
          } else {
            discrete.add(cursor.getLong(3));
          }
        } else {
          if (field.isStringField()) {
            measurement.put(field.fieldName, cursor.getString(3));
          } else {
            measurement.put(field.fieldName, cursor.getLong(3));
          }
        }

        cursor.moveToNext();
        continue;
      }
      days.put(HealthReportUtils.getDateStringForDay(lastDate), dateObject);
    } finally {
      cursor.close();
    }
    return days;
  }

  @SuppressWarnings("unchecked")
  protected JSONObject getEnvironmentsJSON(Environment currentEnvironment,
                                           SparseArray<Environment> envs) {
    JSONObject environments = new JSONObject();

    // Always do this, even if it hasn't recorded anything in the DB.
    environments.put("current", jsonify(currentEnvironment, null));

    String currentHash = currentEnvironment.getHash();
    for (int i = 0; i < envs.size(); i++) {
      Environment e = envs.valueAt(i);
      if (currentHash.equals(e.getHash())) {
        continue;
      }
      environments.put(e.getHash(), jsonify(e, currentEnvironment));
    }
    return environments;
  }

  @SuppressWarnings("unchecked")
  private JSONObject jsonify(Environment e, Environment current) {
    JSONObject age = getProfileAge(e, current);
    JSONObject sysinfo = getSysInfo(e, current);
    JSONObject gecko = getGeckoInfo(e, current);
    JSONObject appinfo = getAppInfo(e, current);
    JSONObject counts = getAddonCounts(e, current);

    JSONObject out = new JSONObject();
    if (age != null)
      out.put("org.mozilla.profile.age", age);
    if (sysinfo != null)
      out.put("org.mozilla.sysinfo.sysinfo", sysinfo);
    if (gecko != null)
      out.put("geckoAppInfo", gecko);
    if (appinfo != null)
      out.put("org.mozilla.appInfo.appinfo", appinfo);
    if (counts != null)
      out.put("org.mozilla.addons.counts", counts);

    JSONObject active = getActiveAddons(e, current);
    if (active != null)
      out.put("org.mozilla.addons.active", active);

    if (current == null) {
      out.put("hash", e.getHash());
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private JSONObject getProfileAge(Environment e, Environment current) {
    JSONObject age = new JSONObject();
    int changes = 0;
    if (current == null || current.profileCreation != e.profileCreation) {
      age.put("profileCreation", e.profileCreation);
      changes++;
    }
    if (current != null && changes == 0) {
      return null;
    }
    age.put("_v", 1);
    return age;
  }

  @SuppressWarnings("unchecked")
  private JSONObject getSysInfo(Environment e, Environment current) {
    JSONObject sysinfo = new JSONObject();
    int changes = 0;
    if (current == null || current.cpuCount != e.cpuCount) {
      sysinfo.put("cpuCount", e.cpuCount);
      changes++;
    }
    if (current == null || current.memoryMB != e.memoryMB) {
      sysinfo.put("memoryMB", e.memoryMB);
      changes++;
    }
    if (current == null || !current.architecture.equals(e.architecture)) {
      sysinfo.put("architecture", e.architecture);
      changes++;
    }
    if (current == null || !current.sysName.equals(e.sysName)) {
      sysinfo.put("name", e.sysName);
      changes++;
    }
    if (current == null || !current.sysVersion.equals(e.sysVersion)) {
      sysinfo.put("version", e.sysVersion);
      changes++;
    }
    if (current != null && changes == 0) {
      return null;
    }
    sysinfo.put("_v", 1);
    return sysinfo;
  }

  @SuppressWarnings("unchecked")
  private JSONObject getGeckoInfo(Environment e, Environment current) {
    JSONObject gecko = new JSONObject();
    int changes = 0;
    if (current == null || !current.vendor.equals(e.vendor)) {
      gecko.put("vendor", e.vendor);
      changes++;
    }
    if (current == null || !current.appName.equals(e.appName)) {
      gecko.put("name", e.appName);
      changes++;
    }
    if (current == null || !current.appID.equals(e.appID)) {
      gecko.put("id", e.appID);
      changes++;
    }
    if (current == null || !current.appVersion.equals(e.appVersion)) {
      gecko.put("version", e.appVersion);
      changes++;
    }
    if (current == null || !current.appBuildID.equals(e.appBuildID)) {
      gecko.put("appBuildID", e.appBuildID);
      changes++;
    }
    if (current == null || !current.platformVersion.equals(e.platformVersion)) {
      gecko.put("platformVersion", e.platformVersion);
      changes++;
    }
    if (current == null || !current.platformBuildID.equals(e.platformBuildID)) {
      gecko.put("platformBuildID", e.platformBuildID);
      changes++;
    }
    if (current == null || !current.os.equals(e.os)) {
      gecko.put("os", e.os);
      changes++;
    }
    if (current == null || !current.xpcomabi.equals(e.xpcomabi)) {
      gecko.put("xpcomabi", e.xpcomabi);
      changes++;
    }
    if (current == null || !current.updateChannel.equals(e.updateChannel)) {
      gecko.put("updateChannel", e.updateChannel);
      changes++;
    }
    if (current != null && changes == 0) {
      return null;
    }
    gecko.put("_v", 1);
    return gecko;
  }

  @SuppressWarnings("unchecked")
  private JSONObject getAppInfo(Environment e, Environment current) {
    JSONObject appinfo = new JSONObject();
    int changes = 0;
    if (current == null || current.isBlocklistEnabled != e.isBlocklistEnabled) {
      appinfo.put("isBlocklistEnabled", e.isBlocklistEnabled);
      changes++;
    }
    if (current == null || current.isTelemetryEnabled != e.isTelemetryEnabled) {
      appinfo.put("isTelemetryEnabled", e.isTelemetryEnabled);
      changes++;
    }
    if (current != null && changes == 0) {
      return null;
    }
    appinfo.put("_v", 2);
    return appinfo;
  }

  @SuppressWarnings("unchecked")
  private JSONObject getAddonCounts(Environment e, Environment current) {
    JSONObject counts = new JSONObject();
    int changes = 0;
    if (current == null || current.extensionCount != e.extensionCount) {
      counts.put("extension", e.extensionCount);
      changes++;
    }
    if (current == null || current.pluginCount != e.pluginCount) {
      counts.put("plugin", e.pluginCount);
      changes++;
    }
    if (current == null || current.themeCount != e.themeCount) {
      counts.put("theme", e.themeCount);
      changes++;
    }
    if (current != null && changes == 0) {
      return null;
    }
    counts.put("_v", 1);
    return counts;
  }

  @SuppressWarnings("unchecked")
  private JSONObject getActiveAddons(Environment e, Environment current) {
    JSONObject active = new JSONObject();
    int changes = 0;
    if (current != null && changes == 0) {
      return null;
    }
    active.put("_v", 1);
    return active;
  }
}
