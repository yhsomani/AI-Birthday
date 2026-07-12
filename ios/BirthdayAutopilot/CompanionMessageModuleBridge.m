#if !defined(BIRTHDAY_E2E) && !defined(BIRTHDAY_SMOKE)

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(CompanionMessageModule, NSObject)

RCT_EXTERN_METHOD(canPresent:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(prepareComposerReview:(NSDictionary *)request
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(presentUserConfirmedComposer:(NSDictionary *)request
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end

#endif  // !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
