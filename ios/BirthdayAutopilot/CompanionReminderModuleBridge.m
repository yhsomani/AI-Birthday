#if !defined(BIRTHDAY_E2E) && !defined(BIRTHDAY_SMOKE)

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(CompanionReminderModule, NSObject)

RCT_EXTERN_METHOD(getStatus:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(requestAuthorization:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(openNotificationSettings:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end

#endif  // !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
