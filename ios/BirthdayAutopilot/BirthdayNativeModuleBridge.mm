#if !defined(BIRTHDAY_E2E) && !defined(BIRTHDAY_SMOKE)

#import <BirthdayNativeSpec/BirthdayNativeSpec.h>
#import <React/RCTEventEmitter.h>
#import <UIKit/UIKit.h>

#import "BirthdayAutopilot-Swift.h"

static NSString *const BirthdayNativeInvalidationEvent = @"BirthdayNativeInvalidated";
static NSString *const BirthdayNativeRouteAvailableEvent = @"BirthdayNativeRouteAvailable";
static NSString *const CompanionStoreChangeNotification =
    @"BirthdayAutopilot.CompanionProtectedStoreDidChange";
static NSString *const CompanionNativeRouteNotification =
    @"BirthdayAutopilot.CompanionNativeRouteAvailable";

@interface BirthdayNative : RCTEventEmitter <NativeBirthdaySpec>
@end

@interface BirthdayNative ()
- (void)sendInvalidationWithRevision:(NSString *)revision;
@end

@implementation BirthdayNative {
  BirthdayNativeService *_service;
  NSMutableArray<id<NSObjectProtocol>> *_notificationTokens;
  BOOL _observing;
}

RCT_EXPORT_MODULE(BirthdayNative)

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

- (dispatch_queue_t)methodQueue
{
  return dispatch_get_main_queue();
}

- (instancetype)init
{
  self = [super init];
  if (self) {
    _service = [BirthdayNativeService new];
    _notificationTokens = [NSMutableArray new];
  }
  return self;
}

- (NSArray<NSString *> *)supportedEvents
{
  return @[ BirthdayNativeInvalidationEvent, BirthdayNativeRouteAvailableEvent ];
}

RCT_EXPORT_METHOD(getProjection:(NSString *)area
                  requestJson:(NSString *)requestJson
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  (void)reject;
  [_service getProjection:area
              requestJson:requestJson
               completion:^(NSDictionary *response) {
                 resolve(response);
               }];
}

RCT_EXPORT_METHOD(executeUserIntent:(NSString *)intent
                  expectedRevision:(NSString *_Nullable)expectedRevision
                  payloadJson:(NSString *)payloadJson
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  (void)reject;
  [_service executeUserIntent:intent
             expectedRevision:expectedRevision
                   payloadJson:payloadJson
                   completion:^(NSDictionary *response) {
                     resolve(response);
                   }];
}

- (void)startObserving
{
  if (_observing) {
    return;
  }
  _observing = YES;
  NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
  __weak BirthdayNative *weakSelf = self;
  id<NSObjectProtocol> storeToken = [center
      addObserverForName:CompanionStoreChangeNotification
                  object:nil
                   queue:[NSOperationQueue mainQueue]
              usingBlock:^(NSNotification *notification) {
                BirthdayNative *strongSelf = weakSelf;
                NSString *revision = notification.userInfo[@"revision"];
                if (strongSelf == nil || ![revision isKindOfClass:[NSString class]]) {
                  return;
                }
                [strongSelf sendInvalidationWithRevision:revision];
              }];
  [_notificationTokens addObject:storeToken];

  id<NSObjectProtocol> routeToken = [center
      addObserverForName:CompanionNativeRouteNotification
                  object:nil
                   queue:[NSOperationQueue mainQueue]
              usingBlock:^(NSNotification *notification) {
                BirthdayNative *strongSelf = weakSelf;
                NSString *kind = notification.userInfo[@"kind"];
                if (strongSelf == nil || !strongSelf->_observing ||
                    ![kind isEqualToString:@"available"]) {
                  return;
                }
                [strongSelf sendEventWithName:BirthdayNativeRouteAvailableEvent
                                         body:@{ @"kind" : @"available" }];
              }];
  [_notificationTokens addObject:routeToken];

  NSArray<NSNotificationName> *lifecycleNames = @[
    UIApplicationDidBecomeActiveNotification,
    UIApplicationWillResignActiveNotification,
    UIApplicationProtectedDataDidBecomeAvailable,
    UIApplicationProtectedDataWillBecomeUnavailable,
  ];
  for (NSNotificationName name in lifecycleNames) {
    id<NSObjectProtocol> token = [center
        addObserverForName:name
                    object:nil
                     queue:[NSOperationQueue mainQueue]
                usingBlock:^(__unused NSNotification *notification) {
                  BirthdayNative *strongSelf = weakSelf;
                  if (strongSelf == nil || !strongSelf->_observing) {
                    return;
                  }
                  [strongSelf->_service currentRevision:^(NSString *revision) {
                    [strongSelf sendInvalidationWithRevision:revision];
                  }];
                }];
    [_notificationTokens addObject:token];
  }
}

- (void)stopObserving
{
  _observing = NO;
  NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
  for (id<NSObjectProtocol> token in _notificationTokens) {
    [center removeObserver:token];
  }
  [_notificationTokens removeAllObjects];
}

- (void)invalidate
{
  [self stopObserving];
  [super invalidate];
}

- (void)dealloc
{
  NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
  for (id<NSObjectProtocol> token in _notificationTokens) {
    [center removeObserver:token];
  }
}

- (void)sendInvalidationWithRevision:(NSString *)revision
{
  static NSCharacterSet *nonDecimalCharacters;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    nonDecimalCharacters =
        [[NSCharacterSet characterSetWithCharactersInString:@"0123456789"] invertedSet];
  });
  BOOL hasLeadingZero = revision.length > 1 && [revision hasPrefix:@"0"];
  if (!_observing || revision.length == 0 || revision.length > 19 || hasLeadingZero ||
      [revision rangeOfCharacterFromSet:nonDecimalCharacters].location != NSNotFound) {
    return;
  }
  [self sendEventWithName:BirthdayNativeInvalidationEvent
                     body:@{
                       @"revision" : revision,
                       @"areas" : @[
                         @"bootstrap",
                         @"setup",
                         @"home",
                         @"eligibility",
                         @"readiness",
                         @"account",
                         @"contacts",
                         @"messages",
                         @"automation",
                         @"activity",
                         @"privacy",
                         @"route",
                       ],
                     }];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeBirthdaySpecJSI>(params);
}

@end

#endif  // !BIRTHDAY_E2E && !BIRTHDAY_SMOKE
