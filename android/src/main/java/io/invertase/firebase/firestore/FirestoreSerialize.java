package io.invertase.firebase.firestore;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;
import java.util.Map;

public class FirestoreSerialize {
  private static final String TAG = "FirestoreSerialize";
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
  private static final String KEY_CHANGES = "changes";
  private static final String KEY_DATA = "data";
  private static final String KEY_DOC_CHANGE_DOCUMENT = "document";
  private static final String KEY_DOC_CHANGE_NEW_INDEX = "newIndex";
  private static final String KEY_DOC_CHANGE_OLD_INDEX = "oldIndex";
  private static final String KEY_DOC_CHANGE_TYPE = "type";
  private static final String KEY_DOCUMENTS = "documents";
  private static final String KEY_METADATA = "metadata";
  private static final String KEY_PATH = "path";

  /**
   * Convert a DocumentSnapshot instance into a React Native WritableMap
   *
   * @param documentSnapshot DocumentSnapshot
   * @return WritableMap
   */
  static WritableMap snapshotToWritableMap(DocumentSnapshot documentSnapshot) {
    WritableMap documentMap = Arguments.createMap();

    documentMap.putString(KEY_PATH, documentSnapshot.getReference().getPath());
    if (documentSnapshot.exists()) {
      documentMap.putMap(KEY_DATA, objectMapToWritable(documentSnapshot.getData()));
    }
    // metadata
    if (documentSnapshot.getMetadata() != null) {
      WritableMap metadata = Arguments.createMap();
      metadata.putBoolean("fromCache", documentSnapshot.getMetadata().isFromCache());
      metadata.putBoolean("hasPendingWrites", documentSnapshot.getMetadata().hasPendingWrites());
      documentMap.putMap(KEY_METADATA, metadata);
    }

    return documentMap;
  }

  public static WritableMap snapshotToWritableMap(QuerySnapshot querySnapshot) {
    WritableMap queryMap = Arguments.createMap();

    List<DocumentChange> documentChanges = querySnapshot.getDocumentChanges();
    queryMap.putArray(KEY_CHANGES, documentChangesToWritableArray(documentChanges));

    // documents
    WritableArray documents = Arguments.createArray();
    List<DocumentSnapshot> documentSnapshots = querySnapshot.getDocuments();
    for (DocumentSnapshot documentSnapshot : documentSnapshots) {
      documents.pushMap(snapshotToWritableMap(documentSnapshot));
    }
    queryMap.putArray(KEY_DOCUMENTS, documents);

    // metadata
    if (querySnapshot.getMetadata() != null) {
      WritableMap metadata = Arguments.createMap();
      metadata.putBoolean("fromCache", querySnapshot.getMetadata().isFromCache());
      metadata.putBoolean("hasPendingWrites", querySnapshot.getMetadata().hasPendingWrites());
      queryMap.putMap(KEY_METADATA, metadata);
    }

    return queryMap;
  }

  /**
   * Convert a List of DocumentChange instances into a React Native WritableArray
   *
   * @param documentChanges List<DocumentChange>
   * @return WritableArray
   */
  static WritableArray documentChangesToWritableArray(List<DocumentChange> documentChanges) {
    WritableArray documentChangesWritable = Arguments.createArray();
    for (DocumentChange documentChange : documentChanges) {
      documentChangesWritable.pushMap(documentChangeToWritableMap(documentChange));
    }
    return documentChangesWritable;
  }

  /**
   * Convert a DocumentChange instance into a React Native WritableMap
   *
   * @param documentChange DocumentChange
   * @return WritableMap
   */
  static WritableMap documentChangeToWritableMap(DocumentChange documentChange) {
    WritableMap documentChangeMap = Arguments.createMap();

    switch (documentChange.getType()) {
      case ADDED:
        documentChangeMap.putString(KEY_DOC_CHANGE_TYPE, "added");
        break;
      case REMOVED:
        documentChangeMap.putString(KEY_DOC_CHANGE_TYPE, "removed");
        break;
      case MODIFIED:
        documentChangeMap.putString(KEY_DOC_CHANGE_TYPE, "modified");
    }

    documentChangeMap.putMap(KEY_DOC_CHANGE_DOCUMENT,
      snapshotToWritableMap(documentChange.getDocument()));
    documentChangeMap.putInt(KEY_DOC_CHANGE_NEW_INDEX, documentChange.getNewIndex());
    documentChangeMap.putInt(KEY_DOC_CHANGE_OLD_INDEX, documentChange.getOldIndex());

    return documentChangeMap;
  }

  /**
   * Converts an Object Map into a React Native WritableMap.
   *
   * @param map Map<String, Object>
   * @return WritableMap
   */
  static WritableMap objectMapToWritable(Map<String, Object> map) {
    WritableMap writableMap = Arguments.createMap();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      WritableMap typeMap = buildTypeMap(entry.getValue());
      writableMap.putMap(entry.getKey(), typeMap);
    }
    return writableMap;
  }

  /**
   * Converts an Object array into a React Native WritableArray.
   *
   * @param array Object[]
   * @return WritableArray
   */
  static WritableArray objectArrayToWritable(Object[] array) {
    WritableArray writableArray = Arguments.createArray();

    for (Object item : array) {
      if (item == null) {
        writableArray.pushNull();
        continue;
      }

      Class itemClass = item.getClass();

      if (itemClass == Boolean.class) {
        writableArray.pushBoolean((Boolean) item);
      } else if (itemClass == Integer.class) {
        writableArray.pushDouble(((Integer) item).doubleValue());
      } else if (itemClass == Long.class) {
        writableArray.pushDouble(((Long) item).doubleValue());
      } else if (itemClass == Double.class) {
        writableArray.pushDouble((Double) item);
      } else if (itemClass == Float.class) {
        writableArray.pushDouble(((Float) item).doubleValue());
      } else if (itemClass == String.class) {
        writableArray.pushString(item.toString());
      } else if (Map.class.isAssignableFrom(itemClass)) {
        writableArray.pushMap((objectMapToWritable((Map<String, Object>) item)));
      } else if (List.class.isAssignableFrom(itemClass)) {
        List<Object> list = (List<Object>) item;
        Object[] listAsArray = list.toArray(new Object[list.size()]);
        writableArray.pushArray(objectArrayToWritable(listAsArray));
      } else {
        throw new RuntimeException("Cannot convert object of type " + itemClass);
      }
    }

    return writableArray;
  }

  /**
   * Detects an objects type and creates a Map to represent the type and value.
   *
   * @param value Object
   */
  private static WritableMap buildTypeMap(Object value) {
    WritableMap typeMap = Arguments.createMap();
    if (value == null) {
      typeMap.putString("type", "null");
      typeMap.putNull("value");
    } else {
      Class valueClass = value.getClass();

      if (valueClass == Boolean.class) {
        typeMap.putString("type", "boolean");
        typeMap.putBoolean("value", (Boolean) value);
      } else if (valueClass == Integer.class) {
        map.putDouble(key, ((Integer) value).doubleValue());
      } else if (valueClass == Long.class) {
        map.putDouble(key, ((Long) value).doubleValue());
      } else if (valueClass == Double.class) {
        typeMap.putString("type", "number");
        typeMap.putDouble("value", (Double) value);
      } else if (valueClass == Float.class) {
        typeMap.putString("type", "number");
        typeMap.putDouble("value", ((Float) value).doubleValue());
      } else if (valueClass == String.class) {
        map.putString(key, value.toString());
      } else if (Map.class.isAssignableFrom(valueClass)) {
        map.putMap(key, (objectMapToWritable((Map<String, Object>) value)));
      }  else if (List.class.isAssignableFrom(valueClass)) {
        List<Object> list = (List<Object>) value;
        Object[] array = list.toArray(new Object[list.size()]);
        typeMap.putArray("value", objectArrayToWritable(array));
      } else if (valueClass == DocumentReference.class) {
        typeMap.putString("type", "reference");
        typeMap.putString("value", ((DocumentReference) value).getPath());
      } else if (valueClass == GeoPoint.class) {
        typeMap.putString("type", "geopoint");
        WritableMap geoPoint = Arguments.createMap();
        geoPoint.putDouble("latitude", ((GeoPoint) value).getLatitude());
        geoPoint.putDouble("longitude", ((GeoPoint) value).getLongitude());
        typeMap.putMap("value", geoPoint);
      } else if (valueClass == Date.class) {
        typeMap.putString("type", "date");
        typeMap.putString("value", DATE_FORMAT.format((Date) value));
      } else {
        // TODO: Changed to log an error rather than crash - is this correct?
        Log.e(TAG, "buildTypeMap", new RuntimeException("Cannot convert object of type " + valueClass));
        typeMap.putString("type", "null");
        typeMap.putNull("value");
      }
    }

    return typeMap;
  }

  static Map<String, Object> parseReadableMap(FirebaseFirestore firestore, ReadableMap readableMap) {
    Map<String, Object> map = new HashMap<>();
    if (readableMap != null) {
      ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
      while (iterator.hasNextKey()) {
        String key = iterator.nextKey();
        map.put(key, parseTypeMap(firestore, readableMap.getMap(key)));
      }
    }
    return map;
  }

  static List<Object> parseReadableArray(FirebaseFirestore firestore, ReadableArray readableArray) {
    List<Object> list = new ArrayList<>();
    if (readableArray != null) {
      for (int i = 0; i < readableArray.size(); i++) {
        list.add(parseTypeMap(firestore, readableArray.getMap(i)));
      }
    }
    return list;
  }

  static Object parseTypeMap(FirebaseFirestore firestore, ReadableMap typeMap) {
    String type = typeMap.getString("type");
    if ("boolean".equals(type)) {
      return typeMap.getBoolean("value");
    } else if ("number".equals(type)) {
      return typeMap.getDouble("value");
    } else if ("string".equals(type)) {
      return typeMap.getString("value");
    } else if ("null".equals(type)) {
      return null;
    } else if ("array".equals(type)) {
      return parseReadableArray(firestore, typeMap.getArray("value"));
    } else if ("object".equals(type)) {
      return parseReadableMap(firestore, typeMap.getMap("value"));
    } else if ("reference".equals(type)) {
      String path = typeMap.getString("value");
      return firestore.document(path);
    } else if ("geopoint".equals(type)) {
      ReadableMap geoPoint = typeMap.getMap("value");
      return new GeoPoint(geoPoint.getDouble("latitude"), geoPoint.getDouble("longitude"));
    } else if ("date".equals(type)) {
      try {
        String date = typeMap.getString("value");
        return DATE_FORMAT.parse(date);
      } catch (ParseException exception) {
        Log.e(TAG, "parseTypeMap", exception);
        return null;
      }
    } else {
      Log.e(TAG, "parseTypeMap", new RuntimeException("Cannot convert object of type " + type));
      return null;
    }
  }
}
