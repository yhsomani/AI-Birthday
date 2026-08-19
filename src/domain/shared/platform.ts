export type PlatformCapability = Readonly<{
  platform: 'android';
  deliveryMode: 'unattended-device-sms';
  minimumApiLevel: 29;
  unattendedSms: 'release-gated';
  userComposer: 'available-as-explicit-alternative';
}>;
