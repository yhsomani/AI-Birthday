#ifdef BIRTHDAY_SMOKE

#import <BirthdayNativeSpec/BirthdayNativeSpec.h>
#import <Foundation/Foundation.h>
#import <React/RCTEventEmitter.h>

static NSUInteger const BirthdaySmokeMaximumFixtureBytes = 256 * 1024;
static NSUInteger const BirthdaySmokeMaximumRequestCharacters = 64 * 1024;
static NSString *const BirthdaySmokeFallbackRevision = @"1";
static NSString *const BirthdaySmokeFallbackGeneratedAt = @"2026-07-12T00:00:00.000Z";

static BOOL BirthdaySmokeRevisionIsValid(id value)
{
  if (![value isKindOfClass:[NSString class]]) {
    return NO;
  }
  NSString *revision = value;
  if (revision.length == 0 || revision.length > 19 ||
      (revision.length > 1 && [revision hasPrefix:@"0"])) {
    return NO;
  }
  NSCharacterSet *nonDecimal =
      [[NSCharacterSet characterSetWithCharactersInString:@"0123456789"] invertedSet];
  return [revision rangeOfCharacterFromSet:nonDecimal].location == NSNotFound;
}

static NSDictionary<NSString *, id> *BirthdaySmokeDocument(void)
{
  static NSDictionary<NSString *, id> *document;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    NSString *path = [[NSBundle mainBundle] pathForResource:@"production-smoke-projections"
                                                     ofType:@"json"];
    NSData *data = path == nil ? nil : [NSData dataWithContentsOfFile:path options:0 error:nil];
    if (data.length == 0 || data.length > BirthdaySmokeMaximumFixtureBytes) {
      return;
    }
    id decoded = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (![decoded isKindOfClass:[NSDictionary class]]) {
      return;
    }
    NSDictionary *candidate = decoded;
    NSDictionary *platforms = candidate[@"platforms"];
    NSDictionary *ios = [platforms isKindOfClass:[NSDictionary class]] ? platforms[@"ios"] : nil;
    NSDictionary *intentProblem = candidate[@"intentProblem"];
    if (![candidate[@"schemaVersion"] isEqual:@1] ||
        !BirthdaySmokeRevisionIsValid(candidate[@"revision"]) ||
        ![candidate[@"generatedAt"] isKindOfClass:[NSString class]] ||
        ![ios isKindOfClass:[NSDictionary class]] ||
        ![intentProblem isKindOfClass:[NSDictionary class]] ||
        ![intentProblem[@"kind"] isEqual:@"unsupported"] ||
        ![intentProblem[@"code"] isEqual:@"distribution-channel-unapproved"]) {
      return;
    }
    document = candidate;
  });
  return document;
}

static NSDictionary<NSString *, id> *BirthdaySmokeProblem(NSString *code)
{
  return @{ @"kind" : @"unsupported", @"code" : code };
}

static NSDictionary<NSString *, id> *BirthdaySmokeResponse(NSString *kind, id payload)
{
  NSDictionary *document = BirthdaySmokeDocument();
  NSData *payloadData = [NSJSONSerialization dataWithJSONObject:payload options:0 error:nil];
  NSString *payloadJSON = payloadData == nil
      ? @"{\"kind\":\"unsupported\",\"code\":\"native-bridge-unavailable\"}"
      : [[NSString alloc] initWithData:payloadData encoding:NSUTF8StringEncoding];
  return @{
    @"contractVersion" : @1,
    @"revision" : document[@"revision"] ?: BirthdaySmokeFallbackRevision,
    @"generatedAt" : document[@"generatedAt"] ?: BirthdaySmokeFallbackGeneratedAt,
    @"kind" : kind,
    @"payloadJson" : payloadJSON,
  };
}

static NSDictionary<NSString *, id> *BirthdaySmokeRequest(NSString *requestJSON)
{
  if (![requestJSON isKindOfClass:[NSString class]] ||
      requestJSON.length > BirthdaySmokeMaximumRequestCharacters) {
    return nil;
  }
  NSData *data = [requestJSON dataUsingEncoding:NSUTF8StringEncoding];
  id decoded = data == nil ? nil : [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
  return [decoded isKindOfClass:[NSDictionary class]] ? decoded : nil;
}

static NSString *BirthdaySmokeProjectionKey(NSString *area, NSDictionary *request)
{
  if (request == nil || ![area isKindOfClass:[NSString class]]) {
    return nil;
  }
  NSSet<NSString *> *directAreas = [NSSet setWithArray:@[
    @"bootstrap", @"setup", @"home", @"eligibility", @"readiness", @"account",
    @"route", @"notifications"
  ]];
  if ([directAreas containsObject:area]) {
    return request.count == 0 ? area : nil;
  }
  NSString *kind = request[@"kind"];
  if (![kind isKindOfClass:[NSString class]]) {
    return nil;
  }
  NSDictionary<NSString *, NSDictionary<NSString *, NSString *> *> *keys = @{
    @"contacts" : @{ @"list" : @"contacts:list" },
    @"messages" : @{
      @"editor" : @"messages:editor",
      @"next-composer-proposal" : @"messages:next-composer-proposal",
    },
    @"automation" : @{
      @"approval" : @"automation:approval",
      @"latest-test" : @"automation:latest-test",
      @"policy-editor" : @"automation:policy-editor",
      @"sender-transfer-operation" : @"automation:sender-transfer-operation",
    },
    @"activity" : @{ @"list" : @"activity:list", @"issues" : @"activity:issues" },
    @"privacy" : @{
      @"inventory" : @"privacy:inventory",
      @"public-resources" : @"privacy:public-resources",
      @"latest-deletion-receipt" : @"privacy:latest-deletion-receipt",
      @"current-operation" : @"privacy:current-operation",
    },
  };
  return keys[area][kind];
}

@interface BirthdayNative : RCTEventEmitter <NativeBirthdaySpec>
@end

@implementation BirthdayNative

RCT_EXPORT_MODULE(BirthdayNative)

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

- (dispatch_queue_t)methodQueue
{
  return dispatch_get_main_queue();
}

- (NSArray<NSString *> *)supportedEvents
{
  return @[ @"BirthdayNativeInvalidated", @"BirthdayNativeRouteAvailable" ];
}

RCT_EXPORT_METHOD(getProjection:(NSString *)area
                  requestJson:(NSString *)requestJson
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  (void)reject;
  NSDictionary *document = BirthdaySmokeDocument();
  NSDictionary *platforms = document[@"platforms"];
  NSDictionary *ios = platforms[@"ios"];
  NSString *key = BirthdaySmokeProjectionKey(area, BirthdaySmokeRequest(requestJson));
  id payload = key == nil ? nil : ios[key];
  resolve(payload == nil
              ? BirthdaySmokeResponse(@"error", BirthdaySmokeProblem(@"native-bridge-unavailable"))
              : BirthdaySmokeResponse(@"ok", payload));
}

RCT_EXPORT_METHOD(executeUserIntent:(NSString *)intent
                  expectedRevision:(NSString *_Nullable)expectedRevision
                  payloadJson:(NSString *)payloadJson
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  (void)intent;
  (void)expectedRevision;
  (void)payloadJson;
  (void)reject;
  // Every intent has one fixed result. No product service, dispatch, storage,
  // permission, MessageUI, reminder, or network path is reachable here.
  resolve(BirthdaySmokeResponse(
      @"error", BirthdaySmokeProblem(@"distribution-channel-unapproved")));
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeBirthdaySpecJSI>(params);
}

@end

#endif  // BIRTHDAY_SMOKE
