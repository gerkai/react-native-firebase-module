#import "RNFirebaseMessaging.h"

#if __has_include(<FirebaseMessaging/FirebaseMessaging.h>)
@import UserNotifications;
#import "RNFirebaseEvents.h"
#import "RNFirebaseUtil.h"
#import <FirebaseMessaging/FirebaseMessaging.h>
#import <FirebaseInstanceID/FIRInstanceID.h>

#import <React/RCTEventDispatcher.h>
#import <React/RCTConvert.h>
#import <React/RCTUtils.h>

// For iOS 10 we need to implement UNUserNotificationCenterDelegate to receive display
// notifications via APNS
#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
@import UserNotifications;

@interface RNFirebaseMessaging () <UNUserNotificationCenterDelegate>
@end
#endif

@implementation RNFirebaseMessaging

static RNFirebaseMessaging *theRNFirebaseMessaging = nil;

+ (nonnull instancetype)instance {
    return theRNFirebaseMessaging;
}

RCT_EXPORT_MODULE()

- (id)init {
    self = [super init];
    if (self != nil) {
        NSLog(@"Setting up RNFirebaseMessaging instance");
        [self configure];
    }
    return self;
}

- (void)configure {
    // Set as delegate for FIRMessaging
    [FIRMessaging messaging].delegate = self;
    
    // Establish Firebase managed data channel
    [FIRMessaging messaging].shouldEstablishDirectChannel = YES;
    
    // If we're on iOS 10 then we need to set this as a delegate for the UNUserNotificationCenter
#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
    [UNUserNotificationCenter currentNotificationCenter].delegate = self;
#endif
    
    // Set static instance for use from AppDelegate
    theRNFirebaseMessaging = self;
}

- (void)dealloc {

}

// ** AppDelegate methods **

// Listen for background messages
- (void)didReceiveRemoteNotification:(nonnull NSDictionary *)userInfo {
    BOOL isFromBackground = (RCTSharedApplication().applicationState == UIApplicationStateInactive);
    NSDictionary *message = [self parseUserInfo:userInfo clickAction:nil openedFromTray:isFromBackground];
    
    [RNFirebaseUtil sendJSEvent:self name:MESSAGING_MESSAGE_RECEIVED body:message];
}

// Listen for background messages
- (void)didReceiveRemoteNotification:(NSDictionary *)userInfo
              fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    BOOL isFromBackground = (RCTSharedApplication().applicationState == UIApplicationStateInactive);
    NSDictionary *message = [self parseUserInfo:userInfo clickAction:nil openedFromTray:isFromBackground];
    
    [RNFirebaseUtil sendJSEvent:self name:MESSAGING_MESSAGE_RECEIVED body:message];
    
    // TODO: FetchCompletionHandler?
}

// ** UNUserNotificationCenterDelegate methods **
#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
// Handle incoming notification messages while app is in the foreground.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    NSDictionary *message = [self parseUNNotification:notification openedFromTray:false];
    
    [RNFirebaseUtil sendJSEvent:self name:MESSAGING_MESSAGE_RECEIVED body:message];
    
    // TODO: Change this to your preferred presentation option
    completionHandler(UNNotificationPresentationOptionNone);
}

// Handle notification messages after display notification is tapped by the user.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
#if defined(__IPHONE_11_0)
         withCompletionHandler:(void(^)(void))completionHandler {
#else
         withCompletionHandler:(void(^)())completionHandler {
#endif
    NSDictionary *userInfo = [self parseUNNotification:response.notification openedFromTray:true];
             
    [RNFirebaseUtil sendJSEvent:self name:MESSAGING_MESSAGE_RECEIVED body:userInfo];
             
    // TODO: Validate this
    completionHandler();
}

#endif

// ** FIRMessagingDelegate methods **

// Listen for FCM tokens
- (void)messaging:(FIRMessaging *)messaging didReceiveRegistrationToken:(NSString *)fcmToken {
    NSLog(@"Received new FCM token: %@", fcmToken);
    [RNFirebaseUtil sendJSEvent:self name:MESSAGING_TOKEN_REFRESHED body:fcmToken];
}

// Listen for data messages in the foreground
- (void)applicationReceivedRemoteMessage:(nonnull FIRMessagingRemoteMessage *)remoteMessage {
    NSDictionary *message = [self parseFIRMessagingRemoteMessage:remoteMessage openedFromTray:false];
    
    [RNFirebaseUtil sendJSEvent:self name:MESSAGING_MESSAGE_RECEIVED body:message];
}

// ** Start React Module methods **
RCT_EXPORT_METHOD(getToken:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    resolve([[FIRInstanceID instanceID] token]);
}

RCT_EXPORT_METHOD(requestPermission:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    if (RCTRunningInAppExtension()) {
        reject(@"request_permission_unavailable", @"requestPermission is not supported in App Extensions", nil);
        return;
    }
    
    if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
        UIUserNotificationType types = (UIUserNotificationTypeSound | UIUserNotificationTypeAlert | UIUserNotificationTypeBadge);
        [RCTSharedApplication() registerUserNotificationSettings:[UIUserNotificationSettings settingsForTypes:types categories:nil]];
        // Unfortunately on iOS 9 or below, there's no way to tell whether the user accepted or
        // rejected the permissions popup
        // TODO: Is there something we can listen for?
        resolve(@{@"status":@"unknown"});
    } else {
        #if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
            // For iOS 10 display notification (sent via APNS)
            UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
            [[UNUserNotificationCenter currentNotificationCenter] requestAuthorizationWithOptions:authOptions completionHandler:^(BOOL granted, NSError * _Nullable error) {
                if (granted) {
                    resolve(@{@"status": @"granted"});
                } else {
                    reject(@"permission_error", @"Failed to grant permission", error);
                }
            }];
        #endif
    }
    
    dispatch_async(dispatch_get_main_queue(), ^{
        [RCTSharedApplication() registerForRemoteNotifications];
    });
}

// Non Web SDK methods

RCT_EXPORT_METHOD(getBadge: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    resolve(@([RCTSharedApplication() applicationIconBadgeNumber]));
}

RCT_EXPORT_METHOD(getInitialMessage:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject){
    NSDictionary *notification = [self bridge].launchOptions[UIApplicationLaunchOptionsRemoteNotificationKey];
    if (notification) {
        NSDictionary *message = [self parseUserInfo:notification clickAction:nil openedFromTray:true];
        resolve(message);
    } else {
        resolve(nil);
    }
}

RCT_EXPORT_METHOD(setBadge: (NSInteger) number) {
    dispatch_async(dispatch_get_main_queue(), ^{
        [RCTSharedApplication() setApplicationIconBadgeNumber:number];
    });
}

RCT_EXPORT_METHOD(subscribeToTopic: (NSString*) topic) {
    [[FIRMessaging messaging] subscribeToTopic:topic];
}

RCT_EXPORT_METHOD(unsubscribeFromTopic: (NSString*) topic) {
    [[FIRMessaging messaging] unsubscribeFromTopic:topic];
}

// ** Start internals **

- (NSDictionary*)parseFIRMessagingRemoteMessage:(FIRMessagingRemoteMessage *)remoteMessage
                                 openedFromTray:(bool)openedFromTray {
    NSDictionary *appData = remoteMessage.appData;
     
    NSMutableDictionary *message = [[NSMutableDictionary alloc] init];
    NSMutableDictionary *data = [[NSMutableDictionary alloc] init];
    for (id k1 in appData) {
        if ([k1 isEqualToString:@"collapse_key"]) {
            message[@"collapseKey"] = appData[@"collapse_key"];
        } else if ([k1 isEqualToString:@"from"]) {
            message[@"from"] = appData[k1];
        } else if ([k1 isEqualToString:@"notification"]) {
            NSDictionary *notification = appData[k1];
            NSMutableDictionary *notif = [[NSMutableDictionary alloc] init];
            for (id k2 in notification) {
                if ([k2 isEqualToString:@"badge"]) {
                    notif[@"badge"] = notification[k2];
                } else if ([k2 isEqualToString:@"body"]) {
                    notif[@"body"] = notification[k2];
                } else if ([k2 isEqualToString:@"body_loc_args"]) {
                    notif[@"bodyLocalizationArgs"] = notification[k2];
                } else if ([k2 isEqualToString:@"body_loc_key"]) {
                    notif[@"bodyLocalizationKey"] = notification[k2];
                } else if ([k2 isEqualToString:@"click_action"]) {
                    notif[@"clickAction"] = notification[k2];
                } else if ([k2 isEqualToString:@"sound"]) {
                    notif[@"sound"] = notification[k2];
                } else if ([k2 isEqualToString:@"subtitle"]) {
                    notif[@"subtitle"] = notification[k2];
                } else if ([k2 isEqualToString:@"title"]) {
                    notif[@"title"] = notification[k2];
                } else if ([k2 isEqualToString:@"title_loc_args"]) {
                    notif[@"titleLocalizationArgs"] = notification[k2];
                } else if ([k2 isEqualToString:@"title_loc_key"]) {
                    notif[@"titleLocalizationKey"] = notification[k2];
                } else {
                    NSLog(@"Unknown notification key: %@", k2);
                }
            }
            message[@"notification"] = notif;
        } else {
            // Assume custom data key
            data[k1] = appData[k1];
        }
    }
    message[@"data"] = data;
    message[@"openedFromTray"] = @(false);
                                     
    return message;
}
    
- (NSDictionary*)parseUNNotification:(UNNotification *)notification
                      openedFromTray:(bool)openedFromTray {
    NSDictionary *userInfo = notification.request.content.userInfo;
    NSString *clickAction = notification.request.content.categoryIdentifier;
    
    return [self parseUserInfo:userInfo clickAction:clickAction openedFromTray:openedFromTray];
}
    
- (NSDictionary*)parseUserInfo:(NSDictionary *)userInfo
                   clickAction:(NSString *) clickAction
                openedFromTray:(bool)openedFromTray {
    NSMutableDictionary *message = [[NSMutableDictionary alloc] init];
    NSMutableDictionary *notif = [[NSMutableDictionary alloc] init];
    NSMutableDictionary *data = [[NSMutableDictionary alloc] init];
    
    for (id k1 in userInfo) {
        if ([k1 isEqualToString:@"aps"]) {
            NSDictionary *aps = userInfo[k1];
            for (id k2 in aps) {
                if ([k2 isEqualToString:@"alert"]) {
                    NSDictionary *alert = aps[k2];
                    for (id k3 in alert) {
                        if ([k3 isEqualToString:@"body"]) {
                            notif[@"body"] = alert[k3];
                        } else if ([k3 isEqualToString:@"loc-args"]) {
                            notif[@"bodyLocalizationArgs"] = alert[k3];
                        } else if ([k3 isEqualToString:@"loc-key"]) {
                            notif[@"bodyLocalizationKey"] = alert[k3];
                        } else if ([k3 isEqualToString:@"subtitle"]) {
                            notif[@"subtitle"] = alert[k3];
                        } else if ([k3 isEqualToString:@"title"]) {
                            notif[@"title"] = alert[k3];
                        } else if ([k3 isEqualToString:@"title-loc-args"]) {
                            notif[@"titleLocalizationArgs"] = alert[k3];
                        } else if ([k3 isEqualToString:@"title-loc-key"]) {
                            notif[@"titleLocalizationKey"] = alert[k3];
                        } else {
                            NSLog(@"Unknown alert key: %@", k2);
                        }
                    }
                } else if ([k2 isEqualToString:@"badge"]) {
                    notif[@"badge"] = aps[k2];
                } else if ([k2 isEqualToString:@"category"]) {
                    notif[@"clickAction"] = aps[k2];
                } else if ([k2 isEqualToString:@"sound"]) {
                    notif[@"sound"] = aps[k2];
                } else {
                    NSLog(@"Unknown aps key: %@", k2);
                }
            }
        } else if ([k1 isEqualToString:@"gcm.message_id"]) {
            message[@"messageId"] = userInfo[k1];
        } else if ([k1 isEqualToString:@"google.c.a.ts"]) {
            message[@"sentTime"] = userInfo[k1];
        } else if ([k1 isEqualToString:@"gcm.n.e"]
                   || [k1 isEqualToString:@"gcm.notification.sound2"]
                   || [k1 isEqualToString:@"google.c.a.c_id"]
                   || [k1 isEqualToString:@"google.c.a.c_l"]
                   || [k1 isEqualToString:@"google.c.a.e"]
                   || [k1 isEqualToString:@"google.c.a.udt"]) {
            // Ignore known keys
        } else {
            // Assume custom data
            data[k1] = userInfo[k1];
        }
    }
    
    if (!notif[@"clickAction"] && clickAction) {
        notif[@"clickAction"] = clickAction;
    }
    
    message[@"data"] = data;
    message[@"notification"] = notif;
    message[@"openedFromTray"] = @(openedFromTray);
    
    return message;
}

- (NSArray<NSString *> *)supportedEvents {
    return @[MESSAGING_MESSAGE_RECEIVED, MESSAGING_TOKEN_REFRESHED];
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

@end

#else
@implementation RNFirebaseMessaging
@end
#endif

