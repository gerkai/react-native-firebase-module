package io.invertase.firebase.links;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Map;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.ShortDynamicLink;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;

import io.invertase.firebase.Utils;

public class RNFirebaseLinks extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {
  private final static String TAG = RNFirebaseLinks.class.getCanonicalName();
  private String mInitialLink = null;
  private boolean mInitialLinkInitialized = false;

  private interface ResolveHandler {
    void onResolved(String url);
  }

  private interface  ErrorHandler {
    void onError(Exception e);
  }

  public RNFirebaseLinks(ReactApplicationContext reactContext) {
    super(reactContext);
    getReactApplicationContext().addActivityEventListener(this);
    getReactApplicationContext().addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "RNFirebaseLinks";
  }


  private void resloveLink(Intent intent, final ResolveHandler resolveHandler, final ErrorHandler errorHandler) {
    FirebaseDynamicLinks.getInstance()
      .getDynamicLink(intent)
      .addOnSuccessListener(new OnSuccessListener<PendingDynamicLinkData>() {
        @Override
        public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData) {
          if (pendingDynamicLinkData != null) {
            Uri deepLinkUri = pendingDynamicLinkData.getLink();
            String url = deepLinkUri.toString();
            resolveHandler.onResolved(url);
          } else {
            resolveHandler.onResolved(null);
          }
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
          errorHandler.onError(e);
        }
      });
  }

  @ReactMethod
  public void getInitialLink(final Promise promise) {
    if (mInitialLinkInitialized) {
      promise.resolve(mInitialLink);
    } else {
      Activity activity = getCurrentActivity();
      if (activity != null) {
        resloveLink(activity.getIntent(), new ResolveHandler() {
          @Override
          public void onResolved(String url) {
            if (url != null) {
              mInitialLink = url;
              Log.d(TAG, "getInitialLink received a new dynamic link from pendingDynamicLinkData");
            }
            Log.d(TAG, "initial link is: " + mInitialLink);
            promise.resolve(mInitialLink);
            mInitialLinkInitialized = true;
          }
        }, new ErrorHandler() {
          @Override
          public void onError(Exception e) {
            Log.e(TAG, "getInitialLink: failed to resolve link", e);
            promise.reject("links/getDynamicLink", e.getMessage(), e);
          }
        });
      } else {
        Log.d(TAG, "getInitialLink: activity is null");
        promise.resolve(null);
      }
    }
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    // Not required for this module
  }

  @Override
  public void onNewIntent(Intent intent) {
    resloveLink(intent, new ResolveHandler() {
      @Override
      public void onResolved(String url) {
        if (url != null) {
          Log.d(TAG, "handleLink: sending link: " + url);
          Utils.sendEvent(getReactApplicationContext(), "dynamic_link_received", url);
        }
      }
    }, new ErrorHandler() {
      @Override
      public void onError(Exception e) {
        Log.e(TAG, "handleLink: failed to resolve link", e);
      }
    });
  }

  @Override
  public void onHostResume() {
    // Not required for this module
  }

  @Override
  public void onHostPause() {
    // Not required for this module
  }

  @Override
  public void onHostDestroy() {
    mInitialLink = null;
    mInitialLinkInitialized = false;
  }

  @ReactMethod
  public void createDynamicLink(final ReadableMap parameters, final Promise promise) {
    try {
      Map<String, Object> m = Utils.recursivelyDeconstructReadableMap(parameters);

      DynamicLink.Builder builder = getDynamicLinkBuilderFromMap(m);
      Uri link = builder.buildDynamicLink().getUri();

      Log.d(TAG, "created dynamic link: " + link.toString());
      promise.resolve(link.toString());
    } catch (Exception ex) {
      Log.e(TAG, "create dynamic link failure " + ex.getMessage());
      promise.reject("links/failure", ex.getMessage(), ex);
    }
  }

  @ReactMethod
  public void createShortDynamicLink(final ReadableMap parameters, final Promise promise) {
    try {
      Map<String, Object> m = Utils.recursivelyDeconstructReadableMap(parameters);

      DynamicLink.Builder builder = getDynamicLinkBuilderFromMap(m);

      Task<ShortDynamicLink> shortLinkTask = getShortDynamicLinkTask(builder, m)
        .addOnCompleteListener(new OnCompleteListener<ShortDynamicLink>() {
          @Override
          public void onComplete(@NonNull Task<ShortDynamicLink> task) {
            if (task.isSuccessful()) {
              Uri shortLink = task.getResult().getShortLink();
              Log.d(TAG, "created short dynamic link: " + shortLink.toString());
              promise.resolve(shortLink.toString());
            } else {
              Log.e(TAG, "create short dynamic link failure " + task.getException().getMessage());
              promise.reject("links/failure", task.getException().getMessage(), task.getException());
            }
          }
        });
    } catch (Exception ex) {
      Log.e(TAG, "create short dynamic link failure " + ex.getMessage());
      promise.reject("links/failure", ex.getMessage(), ex);
    }
  }

  private DynamicLink.Builder getDynamicLinkBuilderFromMap(final Map<String, Object> m) {
    DynamicLink.Builder parametersBuilder = FirebaseDynamicLinks.getInstance().createDynamicLink();
    Map<String, Object> dynamicLinkInfo = (Map<String, Object>) m.get("dynamicLinkInfo");
    if (dynamicLinkInfo != null) {
      try {
        parametersBuilder.setLink(Uri.parse((String) dynamicLinkInfo.get("link")));
        dynamicLinkInfo.remove("link");

        parametersBuilder.setDynamicLinkDomain((String) dynamicLinkInfo.get("dynamicLinkDomain"));
        dynamicLinkInfo.remove("dynamicLinkDomain");

        setAndroidParameters(dynamicLinkInfo, parametersBuilder);
        dynamicLinkInfo.remove("androidInfo");

        setIosParameters(dynamicLinkInfo, parametersBuilder);
        dynamicLinkInfo.remove("iosInfo");

        setSocialMetaTagParameters(dynamicLinkInfo, parametersBuilder);
        dynamicLinkInfo.remove("socialMetaTagInfo");

        if (dynamicLinkInfo.size() > 0) {
          throw new IllegalArgumentException("Invalid arguments: " + dynamicLinkInfo.keySet().toString());
        }
      } catch (Exception e) {
        Log.e(TAG, "error while building parameters " + e.getMessage());
        throw e;
      }
    }
    return parametersBuilder;
  }

  private Task<ShortDynamicLink> getShortDynamicLinkTask(final DynamicLink.Builder builder, final Map<String, Object> m) {
    Map<String, Object> suffix = (Map<String, Object>) m.get("suffix");
    if (suffix != null) {
      String option = (String) suffix.get("option");
      if ("SHORT".equals(option)) {
        return builder.buildShortDynamicLink(ShortDynamicLink.Suffix.SHORT);
      } else if ("UNGUESSABLE".equals(option)) {
        return builder.buildShortDynamicLink(ShortDynamicLink.Suffix.UNGUESSABLE);
      }
    }
    return builder.buildShortDynamicLink();
  }


  private void setAndroidParameters(final Map<String, Object> dynamicLinkInfo, final DynamicLink.Builder parametersBuilder) {
    Map<String, Object> androidParameters = (Map<String, Object>) dynamicLinkInfo.get("androidInfo");
    if (androidParameters != null) {
      if (!androidParameters.containsKey("androidPackageName")) {
        throw new IllegalArgumentException("no androidPackageName was specified.");
      }

      DynamicLink.AndroidParameters.Builder androidParametersBuilder =
        new DynamicLink.AndroidParameters.Builder((String) androidParameters.get("androidPackageName"));
      androidParameters.remove("androidPackageName");

      if (androidParameters.containsKey("androidFallbackLink")) {
        androidParametersBuilder.setFallbackUrl(Uri.parse((String) androidParameters.get("androidFallbackLink")));
        androidParameters.remove("androidFallbackLink");
      }
      if (androidParameters.containsKey("androidMinPackageVersionCode")) {
        androidParametersBuilder.setMinimumVersion(Integer.parseInt((String) androidParameters.get("androidMinPackageVersionCode")));
        androidParameters.remove("androidMinPackageVersionCode");
      }

      if (androidParameters.size() > 0) {
        throw new IllegalArgumentException("Invalid arguments: " + androidParameters.keySet().toString());
      }
      parametersBuilder.setAndroidParameters(androidParametersBuilder.build());
    }
  }

  private void setIosParameters(final Map<String, Object> dynamicLinkInfo, final DynamicLink.Builder parametersBuilder) {
    Map<String, Object> iosParameters = (Map<String, Object>) dynamicLinkInfo.get("iosInfo");
    if (iosParameters != null) {
      if (!iosParameters.containsKey("iosBundleId")) {
        throw new IllegalArgumentException("no iosBundleId was specified.");
      }
      DynamicLink.IosParameters.Builder iosParametersBuilder =
        new DynamicLink.IosParameters.Builder((String) iosParameters.get("iosBundleId"));
      iosParameters.remove("iosBundleId");

      if (iosParameters.containsKey("iosAppStoreId")) {
        iosParametersBuilder.setAppStoreId((String) iosParameters.get("iosAppStoreId"));
        iosParameters.remove("iosAppStoreId");
      }
      if (iosParameters.containsKey("iosCustomScheme")) {
        iosParametersBuilder.setCustomScheme((String) iosParameters.get("iosCustomScheme"));
        iosParameters.remove("iosCustomScheme");
      }
      if (iosParameters.containsKey("iosFallbackLink")) {
        iosParametersBuilder.setFallbackUrl(Uri.parse((String) iosParameters.get("iosFallbackLink")));
        iosParameters.remove("iosFallbackLink");
      }
      if (iosParameters.containsKey("iosIpadBundleId")) {
        iosParametersBuilder.setIpadBundleId((String) iosParameters.get("iosIpadBundleId"));
        iosParameters.remove("iosIpadBundleId");
      }
      if (iosParameters.containsKey("iosIpadFallbackLink")) {
        iosParametersBuilder.setIpadFallbackUrl(Uri.parse((String) iosParameters.get("iosIpadFallbackLink")));
        iosParameters.remove("iosIpadFallbackLink");
      }
      if (iosParameters.containsKey("iosMinPackageVersionCode")) {
        iosParametersBuilder.setMinimumVersion((String) iosParameters.get("iosMinPackageVersionCode"));
        iosParameters.remove("iosMinPackageVersionCode");
      }

      if (iosParameters.size() > 0) {
        throw new IllegalArgumentException("Invalid arguments: " + iosParameters.keySet().toString());
      }
      parametersBuilder.setIosParameters(iosParametersBuilder.build());
    }
  }

  private void setSocialMetaTagParameters(final Map<String, Object> dynamicLinkInfo, final DynamicLink.Builder parametersBuilder) {
    Map<String, Object> socialMetaTagParameters = (Map<String, Object>) dynamicLinkInfo.get("socialMetaTagInfo");
    if (socialMetaTagParameters != null) {
      DynamicLink.SocialMetaTagParameters.Builder socialMetaTagParametersBuilder =
        new DynamicLink.SocialMetaTagParameters.Builder();

      if (socialMetaTagParameters.containsKey("socialDescription")) {
        socialMetaTagParametersBuilder.setDescription((String) socialMetaTagParameters.get("socialDescription"));
        socialMetaTagParameters.remove("socialDescription");
      }
      if (socialMetaTagParameters.containsKey("socialImageLink")) {
        socialMetaTagParametersBuilder.setImageUrl(Uri.parse((String) socialMetaTagParameters.get("socialImageLink")));
        socialMetaTagParameters.remove("socialImageLink");
      }
      if (socialMetaTagParameters.containsKey("socialTitle")) {
        socialMetaTagParametersBuilder.setTitle((String) socialMetaTagParameters.get("socialTitle"));
        socialMetaTagParameters.remove("socialTitle");
      }

      if (socialMetaTagParameters.size() > 0) {
        throw new IllegalArgumentException("Invalid arguments: " + socialMetaTagParameters.keySet().toString());
      }
      parametersBuilder.setSocialMetaTagParameters(socialMetaTagParametersBuilder.build());
    }
  }
}
